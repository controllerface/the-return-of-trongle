#version 460 core

#include "FastNoiseLite.glsl"
#include "map.glsl"

#layout "ViewData.glsl"

layout(quads) in;

in vec2 Tex2[];
in vec3 Normal2[];
in vec3 Tangent2[];
in vec3 Bitangent2[];
in vec3 Color2[];

out vec3 WorldCoord;
out vec2 Tex3;
out vec2 Tex3low;
out vec3 Color3;
out mat3 TBN;
out float fogFactor;

uniform float gHeight0;
uniform float gHeight1;
uniform float gHeight2;
uniform float gHeight3;


float choose_hue(float height)
{
    if (height < gHeight0)
    {
        return 40;
    }
    else if (height < gHeight1)
    {
        return 100;
    }
    else if (height < gHeight2)
    {
        return 170;
    }
    else return 200;
}


vec3 hsvToRgb(float hue, float saturation, float value, bool gammaCorrect)
{
    int h = int(hue / 60.0);
    float f = hue / 60.0 - float(h);
    float p = value * (1.0 - saturation);
    float q = value * (1.0 - f * saturation);
    float t = value * (1.0 - (1.0 - f) * saturation);

    vec3 rgb;
    switch (h)
    {
        case 0:  rgb = vec3(value, t, p); break;
        case 1:  rgb = vec3(q, value, p); break;
        case 2:  rgb = vec3(p, value, t); break;
        case 3:  rgb = vec3(p, q, value); break;
        case 4:  rgb = vec3(t, p, value); break;
        case 5:
        case 6:  rgb = vec3(value, p, q); break;
        default: rgb = vec3(0.0); break;
    }

    if (gammaCorrect)
    {
        rgb.r = (rgb.r <= 0.0031308)
            ? (rgb.r * 12.92)
            : (1.055 * pow(rgb.r, 1.0 / 2.4) - 0.055);

        rgb.g = (rgb.g <= 0.0031308)
            ? (rgb.g * 12.92)
            : (1.055 * pow(rgb.g, 1.0 / 2.4) - 0.055);

        rgb.b = (rgb.b <= 0.0031308)
            ? (rgb.b * 12.92)
            : (1.055 * pow(rgb.b, 1.0 / 2.4) - 0.055);
    }

    return rgb;
}

float perturb_value(vec2 pos, float value, float accuracy, float spread)
{
    if (accuracy < 0.0) accuracy = 0.0;
    if (accuracy > 1.0) accuracy = 1.0;

    float n;
    fnl_state Vnoise = fnlCreateState(872582);
    Vnoise.noise_type = FNL_NOISE_PERLIN;
    Vnoise.fractal_type = FNL_FRACTAL_FBM;
    Vnoise.octaves = 1;
    n = fnlGetNoise2D(Vnoise, pos.x, pos.y);

    float deviationScale = (1.0 - accuracy) * spread;
    float perturbation = (n * 2.0 - 1.0) * deviationScale;

    return value + perturbation;
}

uniform sampler2D terrainLowHeight;
uniform sampler2D terrainMidHeight;
uniform sampler2D terrainHighHeight;
uniform sampler2D terrainTopHeight;

float CalcHeightOffset(float Height, vec2 TexCoords)
{
    float heightOffset;

    if (Height < gHeight0)
    {
        return texture(terrainLowHeight, TexCoords).r;
    }
    else if (Height < gHeight1)
    {
        return texture(terrainMidHeight, TexCoords).r;
    }
    else if (Height < gHeight2)
    {
        return texture(terrainHighHeight, TexCoords).r;
    }
    else
    {
        return texture(terrainTopHeight, TexCoords).r;
    }
}

void main()
{
    vec2 uv = gl_TessCoord.xy;

    vec2 t00 = Tex2[0];
    vec2 t01 = Tex2[1];
    vec2 t10 = Tex2[2];
    vec2 t11 = Tex2[3];

    vec2 t0 = (t01 - t00) * uv.x + t00;
    vec2 t1 = (t11 - t10) * uv.x + t10;
    Tex3 = (t1 - t0) * uv.y + t0;

    vec3 n00 = Normal2[0];
    vec3 n01 = Normal2[1];
    vec3 n10 = Normal2[2];
    vec3 n11 = Normal2[3];
    vec3 n0 = mix(n00, n01, uv.x);
    vec3 n1 = mix(n10, n11, uv.x);
    vec3 Normal3 = normalize(mix(n0, n1, uv.y));

    vec3 tan0 = Tangent2[0];
    vec3 tan1 = Tangent2[1];
    vec3 tan2 = Tangent2[2];
    vec3 tan3 = Tangent2[3];
    vec3 tan00 = mix(tan0, tan1, uv.x);
    vec3 tan10 = mix(tan2, tan3, uv.x);
    vec3 Tangent3 = normalize(mix(tan00, tan10, uv.y));

    vec3 bitan0 = Bitangent2[0];
    vec3 bitan1 = Bitangent2[1];
    vec3 bitan2 = Bitangent2[2];
    vec3 bitan3 = Bitangent2[3];
    vec3 b0 = mix(bitan0, bitan1, uv.x);
    vec3 b1 = mix(bitan2, bitan3, uv.x);
    vec3 Bitangent3 = normalize(mix(b0, b1, uv.y));

    vec3 T = normalize(Tangent3);
    vec3 B = normalize(Bitangent3);
    vec3 N = normalize(Normal3);
    TBN = mat3(T, B, N);

    vec4 p0 = gl_in[0].gl_Position;
    vec4 p1 = gl_in[1].gl_Position;
    vec4 p2 = gl_in[2].gl_Position;
    vec4 p3 = gl_in[3].gl_Position;

    vec4 worldPos = mix(mix(p0, p1, uv.x), mix(p2, p3, uv.x), uv.y);

    float acc = 0.99;
    float spread = 10.0;
    if (worldPos.y > gHeight0 && worldPos.y < gHeight2)
    {
        acc = 0.8f;
        spread = 50.0f;
    }
    else if (worldPos.y > gHeight2 && worldPos.y < gHeight3)
    {
        acc = 0.9f;
        spread = 80.0f;
    }
    else
    {
        acc = 0.95f;
        spread = 100.0f;
    }

    float height_offset = CalcHeightOffset(worldPos.y, uv);

    worldPos.y = worldPos.y + height_offset * 10.0f;
    worldPos.y = perturb_value(worldPos.xz, worldPos.y, acc, spread);

    WorldCoord = worldPos.xyz;

    Tex3low = WorldCoord.xz * 0.002;      // much lower tiling frequency

    fnl_state Vnoise = fnlCreateState(872582);
    fnl_state Snoise = fnlCreateState(398349);
    Vnoise.noise_type = FNL_NOISE_OPENSIMPLEX2;
    Vnoise.fractal_type = FNL_FRACTAL_FBM;
    Snoise.noise_type = FNL_NOISE_OPENSIMPLEX2;
    Snoise.fractal_type = FNL_FRACTAL_FBM;

    float hue = choose_hue(worldPos.y);

    float vn = fnlGetNoise2D(Vnoise, uv.x, uv.y);
    float sn = fnlGetNoise2D(Snoise, uv.x, uv.y);

    sn = map(sn, -1.0f, 1.0f, 0.0f, 1.0f);
    vn = map(vn, -1.0f, 1.0f, 0.0f, 1.0f);

    Color3 = hsvToRgb(hue, sn, vn, true);

    vec3 color0 = Color2[0];
    vec3 color1 = Color2[1];
    vec3 color2 = Color2[2];
    vec3 color3 = Color2[3];
    vec3 c0 = mix(color0, color1, uv.x);
    vec3 c1 = mix(color2, color3, uv.x);
    vec3 base = normalize(mix(c0, c1, uv.y));
    Color3 = mix(base, Color3 ,0.5f );

    gl_Position = viewProjection * worldPos;

    // TODO: Calculate linear fog.
    fogFactor = 0.0f;
}
