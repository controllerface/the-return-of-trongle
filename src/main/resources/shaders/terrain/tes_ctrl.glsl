#version 460 core

#layout "ViewData.glsl"

layout(vertices = 4) out;

in vec2 Tex1[];
out vec2 Tex2[];

in vec3 Normal1[];
out vec3 Normal2[];

in vec3 Tangent1[];
out vec3 Tangent2[];

in vec3 Bitangent1[];
out vec3 Bitangent2[];

in vec3 Color1[];
out vec3 Color2[];

const int MIN_TESS_LEVEL = 32;
const int MAX_TESS_LEVEL = 64;
const float MIN_DISTANCE = 3500;
const float MAX_DISTANCE = 5000;

void main()
{
    gl_out[gl_InvocationID].gl_Position = gl_in[gl_InvocationID].gl_Position;
    Tex2[gl_InvocationID] = Tex1[gl_InvocationID];
    Normal2[gl_InvocationID] = Normal1[gl_InvocationID];
    Tangent2[gl_InvocationID] = Tangent1[gl_InvocationID];
    Bitangent2[gl_InvocationID] = Bitangent1[gl_InvocationID];
    Color2[gl_InvocationID] = Color1[gl_InvocationID];

    if (gl_InvocationID == 0)
    {
        vec4 eyeSpacePos00 = view * gl_in[0].gl_Position;
        vec4 eyeSpacePos01 = view * gl_in[1].gl_Position;
        vec4 eyeSpacePos10 = view * gl_in[2].gl_Position;
        vec4 eyeSpacePos11 = view * gl_in[3].gl_Position;

        float distance00 = clamp((abs(eyeSpacePos00.z)-MIN_DISTANCE) / (MAX_DISTANCE-MIN_DISTANCE), 0.0, 1.0);
        float distance01 = clamp((abs(eyeSpacePos01.z)-MIN_DISTANCE) / (MAX_DISTANCE-MIN_DISTANCE), 0.0, 1.0);
        float distance10 = clamp((abs(eyeSpacePos10.z)-MIN_DISTANCE) / (MAX_DISTANCE-MIN_DISTANCE), 0.0, 1.0);
        float distance11 = clamp((abs(eyeSpacePos11.z)-MIN_DISTANCE) / (MAX_DISTANCE-MIN_DISTANCE), 0.0, 1.0);

        float tessLevel0 = mix(MAX_TESS_LEVEL, MIN_TESS_LEVEL, min(distance10, distance00));
        float tessLevel1 = mix(MAX_TESS_LEVEL, MIN_TESS_LEVEL, min(distance00, distance01));
        float tessLevel2 = mix(MAX_TESS_LEVEL, MIN_TESS_LEVEL, min(distance01, distance11));
        float tessLevel3 = mix(MAX_TESS_LEVEL, MIN_TESS_LEVEL, min(distance11, distance10));

        gl_TessLevelOuter[0] = tessLevel0;
        gl_TessLevelOuter[1] = tessLevel1;
        gl_TessLevelOuter[2] = tessLevel2;
        gl_TessLevelOuter[3] = tessLevel3;

        gl_TessLevelInner[0] = max(tessLevel1, tessLevel3);
        gl_TessLevelInner[1] = max(tessLevel0, tessLevel2);
    }
}
