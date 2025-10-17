#version 460 core

#define PI 3.14159265359
#define MAX_SHININESS 9
#define INTENSITY_SCALE 0.00014641288433382138;

#include "bias.glsl"

#struct "Light.glsl"
#struct "DirectionalLight.glsl"
#struct "PointLight.glsl"
#struct "SpotLight.glsl"

#layout "ViewData.glsl"
#layout "GlobalLight.glsl"

out vec4 fragColor;
in vec4 vColor;
in mat3 TBN;
in vec4 fragPosLightSpace;

flat in float tindex;

in VERT_DATA
{
    vec3 FragPos;
    vec2 TexCoords;
} vert_in;

layout(std430, binding = 0) readonly buffer PointLights
{
    PointLight point_lights[];
};

layout(std430, binding = 1) readonly buffer SpotLights
{
    SpotLight spot_lights[];
};

uniform sampler2DArray diffuseMaps;
uniform sampler2DArray surfaceMaps;
uniform sampler2DArray normalMaps;
uniform samplerCube skybox;
uniform samplerCube skyboxNight;
uniform sampler2DShadow shadowMap;

float calculateShadow(vec3 normal, vec4 fragPosLightSpace)
{
    vec3 lightDir = blend < 0.5f ? sun.direction.xyz : moon.direction.xyz;

    vec3 projCoords = fragPosLightSpace.xyz / fragPosLightSpace.w;
    projCoords = projCoords * 0.5 + 0.5;

    if (projCoords.x < 0.0 || projCoords.x > 1.0 ||
    projCoords.y < 0.0 || projCoords.y > 1.0 ||
    projCoords.z > 1.0) return 0.0;

    float bias = max(0.005 * (1.0 - dot(normal, lightDir)), 0.005);
    return 1.0 - texture(shadowMap, vec3(projCoords.xy, projCoords.z - bias));
}

vec4 computeReflection(vec3 viewDirection, vec3 normal, vec4 diffuseColor, float metallic, float roughness)
{
    float biased = bias(blend, .8);

    ivec2 cubemapSize = textureSize(skybox, 0);
    float mipLevels = 1.0 + floor(log2(max(float(cubemapSize.x), float(cubemapSize.y))));
    float lodLevel = mipLevels * sqrt(roughness);
    vec3 reflected_direction = -reflect(-viewDirection, normal);

    vec4 reflectedColorA = textureLod(skybox, reflected_direction, lodLevel);
    vec4 reflectedColorB = textureLod(skyboxNight, reflected_direction, lodLevel);
    vec4 reflectedColor = mix(reflectedColorA, reflectedColorB, biased);
    float fresnel = pow(1.0 - max(dot(viewDirection, normal), 0.0), 5.0);
    vec4 reflectionContribution = mix(reflectedColor * diffuseColor, reflectedColor, metallic);
    return reflectionContribution * fresnel;
}

void calculate_world_light(vec3 normal, vec3 surface, out vec4 ambient, out vec4 direct)
{
    float aIntensity = mix(sun.light.aIntensity, moon.light.aIntensity, blend);
    float dIntensity = mix(sun.light.dIntensity, moon.light.dIntensity, blend);
    vec3 color = mix(sun.light.color.xyz, moon.light.color.xyz, blend);
    vec3 dir = mix(sun.direction.xyz, moon.direction.xyz, blend);

    float ambient_occlusion = surface.r;
    ambient = vec4(color * aIntensity, 1.0f) * ambient_occlusion;

    vec3 n_norm = normalize(normal);

    float diffuse_factor = max(dot(n_norm, -dir), 0);
    vec4 diffuse_color = vec4(color * dIntensity * diffuse_factor, 1.0f);

    vec3 view_direction = normalize((viewPosition - vert_in.FragPos));
    float roughness = surface.g;

    float shininess = exp2((1.0 - roughness) * MAX_SHININESS);

    float normalization = ((shininess + 2.0) * (shininess + 4.0)) / (8.0 * PI * (pow(2.0, -shininess * 0.5) + shininess));
    vec3 half_vector = normalize(-dir + view_direction);
    float specular_factor = max(dot(n_norm, half_vector), 0);
    specular_factor = pow(specular_factor, shininess) * diffuse_factor * normalization;
    vec4 specular_color = vec4(color * specular_factor, 1.0);

    float metallic = surface.b;
    direct = (1.0 - metallic) * diffuse_color + specular_color;
}

vec4 calculate_directional_light(Light light, vec3 direction, vec3 normal, vec3 surface)
{
    float ambient_occlusion = surface.r;
    vec4 ambient_color = vec4(light.color.xyz * light.aIntensity, 1.0f) * ambient_occlusion;

    vec3 n_norm = normalize(normal);

    float diffuse_factor = max(dot(n_norm, -direction), 0);
    vec4 diffuse_color = vec4(light.color.xyz * light.dIntensity * diffuse_factor, 1.0f);

    vec3 view_direction = normalize((viewPosition - vert_in.FragPos));
    float roughness = surface.g;

    float shininess = exp2((1.0 - roughness) * MAX_SHININESS);

    float normalization = ((shininess + 2.0) * (shininess + 4.0)) / (8.0 * PI * (pow(2.0, -shininess * 0.5) + shininess));
    vec3 half_vector = normalize(-direction + view_direction);
    float specular_factor = max(dot(n_norm, half_vector), 0);
    specular_factor = pow(specular_factor, shininess) * diffuse_factor * normalization;
    vec4 specular_color = vec4(light.color.xyz * specular_factor, 1.0);

    float metallic = surface.b;
    return (1.0 - metallic) * (ambient_color + diffuse_color) + specular_color;
}

vec4 calc_point_light(PointLight point, vec3 normal, vec3 surface)
{
    if (point.light.dIntensity <= 0) return vec4(0.0);

    vec3 direction = vert_in.FragPos - point.position.xyz;
    float distance = length(direction);
    direction = normalize(direction);
    float NdotL = dot(normal, -direction);
    if (NdotL > 0.0)
    {
        float attenuation = max(min(1.0 - pow(distance / point.range, 4.0), 1.0), 0.0) / (distance * distance);
        vec4 color = calculate_directional_light(point.light, direction, normal, surface);
        return color * attenuation;
    }
    return vec4(0.0);
}

vec4 calc_spot_light(SpotLight spot, vec3 normal, vec3 surface)
{
    if (spot.point.light.dIntensity <= 0) return vec4(0.0);

    vec3 light_to_pixel = normalize(vert_in.FragPos - spot.point.position.xyz);
    float spot_factor = dot(light_to_pixel, spot.direction.xyz);

    if (spot_factor > spot.outerCone)
    {
        float NdotL = dot(normal, -spot.direction.xyz);
        if (NdotL > 0.0)
        {
            vec4 color = calc_point_light(spot.point, normal, surface);
            float spot_effect = smoothstep(spot.outerCone, spot.innerCone, spot_factor);
            return color * spot_effect;
        }
    }
    return vec4(0, 0, 0, 0);
}


void main()
{
    vec3 normal = texture(normalMaps, vec3(vert_in.TexCoords.xy, tindex)).rgb;
    normal = (normal * 2.0 - 1.0);
    normal = normalize(TBN * normal);

    vec3 surface = texture(surfaceMaps, vec3(vert_in.TexCoords.xy, tindex)).xyz;
    float shadow = calculateShadow(normal, fragPosLightSpace);

    vec4 ambient, direct;
    calculate_world_light(normal, surface, ambient, direct);
    vec4 light = ambient + direct * (1.0 - shadow);

    for (int i = 0; i < point_light_count; i ++)
    {
        PointLight point_light = point_lights[i];
        light += calc_point_light(point_light, normal, surface);
    }

    for (int j = 0; j < spot_light_count; j ++)
    {
        SpotLight spot_light = spot_lights[j];
        light += calc_spot_light(spot_light, normal, surface);
    }

    vec3 view_direction = normalize(vert_in.FragPos - viewPosition);
    vec4 diffuse = texture(diffuseMaps, vec3(vert_in.TexCoords.xy, tindex));
    vec4 env = computeReflection(view_direction, normal, diffuse, surface.b, surface.g);
    fragColor = diffuse * light + env;

    //use this color to visualze normals for debugging
    //fragColor = vec4(normal * 0.5 + 0.5, 1.0);

    //use this color to visualze shadow values for debugging
    //fragColor = vec4(vec3(1.0 - shadow), 1.0); // white = lit, black = shadow
}
