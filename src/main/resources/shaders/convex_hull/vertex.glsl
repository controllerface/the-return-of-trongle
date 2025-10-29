#version 460 core

#layout "ViewData.glsl"

layout (location = 0) in dvec3 aPos;
layout (location = 2) in vec3 aColor;

out vec3 fColor;

void main()
{
    fColor = aColor;
    gl_Position = viewProjection * vec4(aPos, 1.0);
}
