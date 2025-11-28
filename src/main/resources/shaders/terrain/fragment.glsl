#version 460 core

#include "FastNoiseLite.glsl"
#include "map.glsl"

#struct "Light.glsl"
#struct "DirectionalLight.glsl"

#layout "ViewData.glsl"
#layout "GlobalLight.glsl"

in vec3 WorldCoord;
in vec2 Tex3;
in vec2 Tex3low;
in vec3 Color3;
in mat3 TBN;

out vec4 FragColor;

uniform float gHeight0;
uniform float gHeight1;
uniform float gHeight2;
uniform float gHeight3;

uniform sampler2D terrainLow;
uniform sampler2D terrainMid;
uniform sampler2D terrainHigh;
uniform sampler2D terrainTop;
uniform sampler2D terrainLowNormal;
uniform sampler2D terrainMidNormal;
uniform sampler2D terrainHighNormal;
uniform sampler2D terrainTopNormal;

uniform float uTime;
const float speed1 = 15; // Slow movement (higher altitude)



vec4 distance_mix(sampler2D sampler, float uvScale)
{
    return mix(texture(sampler, Tex3),texture(sampler, Tex3low), uvScale);
}


vec3 normal_mix(sampler2D sampler, float uvScale)
{
    return mix(texture(sampler, Tex3),texture(sampler, Tex3low), uvScale).rgb;
}


vec4 CalcTexColor(float Height, float uvScale)
{
    vec4 TexColor;

    if (Height < gHeight0)
    {
        TexColor = distance_mix(terrainLow, uvScale);
    }
    else if (Height < gHeight1)
    {
        vec4 Color0 = distance_mix(terrainLow, uvScale);
        vec4 Color1 = distance_mix(terrainMid, uvScale);
        float Delta = gHeight1 - gHeight0;
        float Factor = (Height - gHeight0) / Delta;
        float BiasFactor = pow(Factor, 0.25f);
        TexColor = mix(Color0, Color1, BiasFactor);
    }
    else if (Height < gHeight2)
    {
        vec4 Color0 = distance_mix(terrainMid, uvScale);
        vec4 Color1 = distance_mix(terrainHigh, uvScale);
        float Delta = gHeight2 - gHeight1;
        float Factor = (Height - gHeight1) / Delta;
        float BiasFactor = pow(Factor, 0.85f);
        TexColor = mix(Color0, Color1, BiasFactor);
    }
    else if (Height < gHeight3)
    {
        vec4 Color0 = distance_mix(terrainHigh, uvScale);
        vec4 Color1 = distance_mix(terrainTop, uvScale);
        float Delta = gHeight3 - gHeight2;
        float Factor = (Height - gHeight2) / Delta;
        float BiasFactor = pow(Factor, 0.30);
        TexColor = mix(Color0, Color1, BiasFactor);
    }
    else
    {
        TexColor = distance_mix(terrainTop, uvScale);
    }

    return TexColor;
}

vec3 CalcTexNormal(float Height, float uvScale)
{
    vec3 Normal;

    if (Height < gHeight0)
    {
        Normal = normal_mix(terrainLowNormal, uvScale).rgb * 2.0 - 1.0;
    }
    else if (Height < gHeight1)
    {
        vec3 Norm0 = normal_mix(terrainLowNormal, uvScale).rgb * 2.0 - 1.0;
        vec3 Norm1 = normal_mix(terrainMidNormal, uvScale).rgb * 2.0 - 1.0;
        float Delta = gHeight1 - gHeight0;
        float Factor = (Height - gHeight0) / Delta;
        float BiasFactor = pow(Factor, 0.75f);
        Normal = normalize(mix(Norm0, Norm1, Factor));
    }
    else if (Height < gHeight2)
    {
        vec3 Norm0 = normal_mix(terrainMidNormal, uvScale).rgb * 2.0 - 1.0;
        vec3 Norm1 = normal_mix(terrainHighNormal, uvScale).rgb * 2.0 - 1.0;
        float Delta = gHeight2 - gHeight1;
        float Factor = (Height - gHeight1) / Delta;
        float BiasFactor = pow(Factor, 0.85f);
        Normal = normalize(mix(Norm0, Norm1, Factor));
    }
    else if (Height < gHeight3)
    {
        vec3 Norm0 = normal_mix(terrainHighNormal, uvScale).rgb * 2.0 - 1.0;
        vec3 Norm1 = normal_mix(terrainTopNormal, uvScale).rgb * 2.0 - 1.0;
        float Delta = gHeight3 - gHeight2;
        float Factor = (Height - gHeight2) / Delta;
        float BiasFactor = pow(Factor, 0.30);
        Normal = normalize(mix(Norm0, Norm1, BiasFactor));
    }
    else
    {
        Normal = normal_mix(terrainTopNormal, uvScale).rgb * 2.0 - 1.0;
    }

    return Normal;
}

vec4 calculate_world_light(DirectionalLight light, vec3 normal)
{
    vec4 ambient_color = vec4(light.light.color.xyz * light.light.aIntensity, 1.0f);
    vec3 n_norm = normalize(normal);
    float diffuse_factor = max(dot(n_norm, -light.direction.xyz), 0);
    vec4 diffuse_color = vec4(light.light.color.xyz * light.light.dIntensity * diffuse_factor, 1.0f);
    return (ambient_color + diffuse_color);
}

float rand(vec2 co)
{
    float a = 12.9898;
    float b = 78.233;
    float c = 43758.5453;
    float dt = dot(co.xy, vec2(a, b));
    float sn = mod(dt, 3.14);
    return fract(sin(sn) * c);
}

vec3 perturbDirection(vec3 dir, float accuracy, float spread)
{
    accuracy = clamp(accuracy, 0.0, 1.0);

    vec3 arbitrary = abs(dir.x) < 0.99 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);

    vec3 perpendicular_1 = normalize(cross(dir, arbitrary));
    vec3 perpendicular_2 = normalize(cross(dir, perpendicular_1));

    float deviationScale = (1.0 - accuracy) * spread;
    float rand1 = (rand(vec2(dir.xy)) * 2.0 - 1.0) * deviationScale;
    float rand2 = (rand(vec2(dir.yz)) * 2.0 - 1.0) * deviationScale;

    vec3 perturbation = perpendicular_1 * rand1 + perpendicular_2 * rand2;

    //return dir + perturbation;
    return normalize(dir + perturbation);
}

vec3 applyFog(in vec3  rgb, in float distance, in vec3  rayDir, in vec3  sunDir, in float fogDensity)
{
    //float fogAmount = 1.0 - exp( -distance * fogDensity );
    //float fogAmount = clamp((distance * fogDensity) / (distance * fogDensity + 1.0), 0.0, 1.0) * (1.2- blend);
    float fogAmount = smoothstep(0.0, 1.0, 1.0 - exp(-distance * fogDensity));

    float sunFactor = max(dot(rayDir, -sunDir), 0.0);
    sunFactor = pow(sunFactor, 4.0) * (1.0 - blend);// Exaggerate the effect

    float sunAmount = max(dot(rayDir, sunDir), 0.0);
    vec3  fogColor  = mix(vec3(0.1f, 0.1f, 0.15f), // bluish
    vec3(sun.light.color.xyz), // yellowish
    sunFactor);
    return mix(rgb, fogColor, fogAmount);
}

uniform float fogStartDistance = 5000.0f;



void main()
{

    float distance = length(WorldCoord - viewPosition);


    float dist0 = 2.0;   // start blending
    float dist1 = 200.0;  // fully macro beyond this
    float t = clamp((distance - dist0) / (dist1 - dist0), 0.0, 1.0);
    t = smoothstep(0.0, 1.0, t);




    fnl_state Vnoise = fnlCreateState(1581);
    Vnoise.noise_type = FNL_NOISE_OPENSIMPLEX2;
    Vnoise.fractal_type = FNL_FRACTAL_FBM;
    Vnoise.frequency = 0.0001;
    Vnoise.octaves = 3;

    vec2 driftOffset1 = vec2(uTime * speed1, 0);//uTime * speed1 * 0.5);
    //vec2 driftOffset2 = vec2(uTime * speed2, uTime * speed2 * 0.3);

    float vn1 = fnlGetNoise2D(Vnoise, WorldCoord.x + driftOffset1.x, WorldCoord.z + driftOffset1.y);
    //float vn2 = fnlGetNoise2D(Vnoise, WorldXZ.x + driftOffset2.x, WorldXZ.y + driftOffset2.y);

    // Fake a second "layer" by warping or distorting the first one
    float vn2 = vn1 + sin(vn1 * 5.0f + uTime * 0.1) * 0.3;

    vn1 = map(vn1, -1.0f, 1.0f, -2.0f, 2.0f);
    vn2 = map(vn2, -1.0f, 1.0f, -2.0f, 2.0f);

    // Blend the two layers
    float cloudDensity = mix(vn1, vn2, 0.5);
    float cloudShadow = 1 - cloudDensity; //cloudDensity > 0.0f ? 0.5f : 1.0f;
    cloudShadow = max(cloudShadow, 0.5);





    vec3 n_norm = CalcTexNormal(WorldCoord.y, t);
    vec3 normalTangent = n_norm.rgb;

    vec3 normal = normalize(TBN * normalTangent);



    float bMin  = 0.000001f;// Very light fog at high altitudes
    float bMax  = 0.0001f;// Dense fog in valleys or near the ground

    //vec3 mod = perturbDirection(WorldCoord, .95f, 500.0f);

    float avg  = (gHeight1 + gHeight2) / 2.0f;
    float heightFactor = smoothstep(gHeight0, avg, WorldCoord.y);
    float fogDensity = mix(bMax, bMin, heightFactor);










    vec3 rayDir = normalize(WorldCoord - viewPosition);
    vec3 sunDir = normalize(vec3(sun.direction.xyz));


    vec4 diffuse = CalcTexColor(WorldCoord.y, t);
    vec4 light = calculate_world_light(sun, normal) + calculate_world_light(moon, normal);

    //FragColor = (diffuse) * light;
    FragColor = (diffuse * vec4(Color3, 1.0f)) * light;



    vec3 clr = FragColor.xyz;
    clr = applyFog(clr, distance, rayDir, perturbDirection(sunDir, .8f, 0.1f), fogDensity);

    // Calculate distance fog value
    float fogDistFactor = clamp((distance - fogStartDistance) / fogStartDistance, 0.0, 1.0);

    // Apply distance fog (example color — change if needed)
    vec3 fogColor = mix(sun.light.color, moon.light.color, blend).xyz * 0.05f;
    clr = mix(clr, fogColor, fogDistFactor);

    clr *= cloudShadow;
    FragColor.xyz = clr;
    //use this color to visualze normals for debugging
    //FragColor = vec4(normal * 0.5 + 0.5, 1.0);
}
