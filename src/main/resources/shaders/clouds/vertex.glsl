#version 460 core

#layout "ViewData.glsl"

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aUV;

out vec2 TexCoords;
out vec2 WorldXZ;
out vec4 fragPosLightSpace;

uniform mat4 lightSpaceMatrix;

void main()
{
    TexCoords = aUV;
    WorldXZ = aPos.xz;
    gl_Position = viewProjection * vec4(aPos, 1.0);
    fragPosLightSpace = lightSpaceMatrix * vec4(aPos.x, -100.0f, aPos.z, 1.0);
}
