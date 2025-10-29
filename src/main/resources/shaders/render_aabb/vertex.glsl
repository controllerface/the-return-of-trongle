#version 460 core

layout (location = 0) in vec3 inMin;
layout (location = 1) in vec3 inMax;
layout (location = 2) in vec3 inColor;

out vec3 vMin;
out vec3 vMax;
out vec3 vColor;

void main()
{
    vMin = inMin;
    vMax = inMax;
    vColor = inColor;
}
