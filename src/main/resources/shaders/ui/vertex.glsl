#version 460 core

#layout "ViewData.glsl"

layout (location = 0) in vec2 v_position;
layout (location = 1) in vec4 v_color;

out vec4 f_color;
uniform float screenHeight;

void main()
{
    gl_Position = screenProjection * vec4(v_position, 5.0, 1.0);
    f_color = v_color;
}
