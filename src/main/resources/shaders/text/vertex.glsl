#version 460 core

#layout "ViewData.glsl"

layout (location = 0) in vec2 v_position;
layout (location = 1) in vec2 v_tex_coords;
layout (location = 2) in float v_tex_id;

out vec2 f_tex_coords;
flat out float f_tex_id;

void main()
{
    gl_Position = screenProjection * vec4(v_position, 5.0, 1.0);
    f_tex_coords = v_tex_coords;
    f_tex_id = v_tex_id;
}
