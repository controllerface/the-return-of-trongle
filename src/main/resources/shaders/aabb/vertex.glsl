#version 460 core

layout (location = 0) in dvec3 inMin;
layout (location = 2) in dvec3 inMax;
layout (location = 4) in vec3 inColor;

out vec3 vMin;
out vec3 vMax;
out vec3 vColor;

void main()
{
    vMin = vec3(inMin);
    vMax = vec3(inMax);
    vColor = inColor;
}
