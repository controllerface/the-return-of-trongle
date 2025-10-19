#version 460 core

in vec2 f_tex_coords;
flat in float f_tex_id;

out vec4 color;

uniform sampler2DArray uTexture;

void main()
{
    vec3 textColor = vec3(1.0, 1.0, 1.0);
    float r = texture(uTexture, vec3(f_tex_coords.xy, f_tex_id)).r;
    vec4 sampled = vec4(1.0, 1.0, 1.0, r);
    if (r > 0) color = vec4(textColor, 1.0) * sampled;
    else discard;
}
