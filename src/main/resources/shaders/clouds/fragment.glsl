#version 460 core

#include "FastNoiseLite.glsl"
#include "map.glsl"

#struct "Light.glsl"
#struct "DirectionalLight.glsl"

#layout "GlobalLight.glsl"

in vec2 TexCoords;
in vec2 WorldXZ;
in vec4 fragPosLightSpace;

out vec4 FragColor;

uniform sampler2D shadowMap;
uniform float uTime;

const float speed1 = 15;

vec4 calculate_world_light(DirectionalLight light, vec3 normal)
{
    vec4 ambient_color = vec4(light.light.color.xyz * light.light.aIntensity, 1.0f);
    vec3 n_norm = normalize(normal);
    float diffuse_factor = max(dot(n_norm, -light.direction.xyz), 0);
    vec4 diffuse_color = vec4( light.light.color.xyz * light.light.dIntensity * diffuse_factor, 1.0f);
    vec4 f = (ambient_color + diffuse_color);
    f.x = max(f.x, 0.05f);
    f.y = max(f.y, 0.05f);
    f.z = max(f.z, 0.05f);
    return f;
}

float calculateShadow(vec4 lightSpacePos)
{
    //vec4 lightSpacePos = lightSpaceMatrix * vec4(worldPos, 1.0);
    vec3 projCoords = lightSpacePos.xyz / lightSpacePos.w;
    projCoords = projCoords * 0.5 + 0.5; // Map to [0, 1]

    // Check if we're outside the light's frustum
    if (projCoords.z > 1.0 || projCoords.x < 0.0 || projCoords.x > 1.0 || projCoords.y < 0.0 || projCoords.y > 1.0)
    return 1.0; // fully lit

    float closestDepth = texture(shadowMap, projCoords.xy).r;
    float currentDepth = projCoords.z;

    float bias = 0.005; // Tweak this depending on light angle and scene scale
    return (currentDepth - bias) > closestDepth ? 0.0 : 1.0;
}


void main()
{
    fnl_state Vnoise = fnlCreateState(1581);
    Vnoise.noise_type = FNL_NOISE_OPENSIMPLEX2;
    Vnoise.fractal_type = FNL_FRACTAL_FBM;
    Vnoise.frequency = 0.0001;
    Vnoise.octaves = 5;

    vec2 driftOffset1 = vec2(uTime * speed1, 0);//uTime * speed1 * 0.5);
    //vec2 driftOffset2 = vec2(uTime * speed2, uTime * speed2 * 0.3);

    float vn1 = fnlGetNoise2D(Vnoise, WorldXZ.x + driftOffset1.x, WorldXZ.y + driftOffset1.y);
    //float vn2 = fnlGetNoise2D(Vnoise, WorldXZ.x + driftOffset2.x, WorldXZ.y + driftOffset2.y);

    // Fake a second "layer" by warping the first one
    float vn2 = vn1 + sin(vn1 * 5.0f + uTime * 0.1) * 0.3;

    vn1 = map(vn1, -1.0f, 1.0f, -2.0f, 2.0f);
    vn2 = map(vn2, -1.0f, 1.0f, -2.0f, 2.0f);

    // Blend the two layers
    float cloudDensity = mix(vn1, vn2, 0.5);
    //float cloudDensity = vn1;

    vec2 delta = vec2(0.002, 0.002);
    float height = vn1; // Use first noise layer as height map

    // Compute normal from height differences
//    float nx = height - fnlGetNoise2D(Vnoise, WorldXZ.x + delta.x, WorldXZ.y);
//    float nz = height - fnlGetNoise2D(Vnoise, WorldXZ.x, WorldXZ.y + delta.y);

    // Fake derivatives based on the value itself
    float nx = sin(height * 0.1 + uTime) * 0.1;
    float nz = cos(height * 0.1 + uTime) * 0.1;

//    float nx = (height * 0.5) + (WorldXZ.x * 0.01);
//    float nz = (height * 0.5) + (WorldXZ.y * 0.01);

    vec3 normal = normalize(vec3(nx, 1.0, nz));

    // flip normals when rendering the underside of the plane
//    if (!gl_FrontFacing) {
//        normal = -normal;
//    }

    //float shadow = calculateShadow(fragPosLightSpace);

    vec4 light = calculate_world_light(sun, normal) + calculate_world_light(moon, normal);
//    light.x *= shadow;
//    light.y *= shadow;
//    light.z *= shadow;

    vec3 litColor = cloudDensity.xxx * light.rgb;
    FragColor = vec4(litColor, 0.5);

    //float a = min(0.05, 0.1 * (1.0f- blend));
//    FragColor = vec4(cloudDensity, cloudDensity, cloudDensity, 0.07f) * light;
}