#version 460 core

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in vec3 inTangent;
layout(location = 3) in vec3 inBitangent;
layout(location = 4) in vec2 inUv;
layout(location = 5) in vec3 inColor;

out vec2 Tex1;
out vec3 Normal1;
out vec3 Tangent1;
out vec3 Bitangent1;
out vec3 Color1;

void main()
{
    gl_Position = vec4(inPosition, 1.0);
    Tex1 = inUv;
    Normal1 = inNormal;
    Tangent1 = inTangent;
    Bitangent1 = inBitangent;
    Color1 = inColor;
}
