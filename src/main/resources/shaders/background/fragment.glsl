#version 460 core

out vec4 FragColor;
in vec2 TexCoord;

uniform sampler2D bgTexture;

void main()
{
    FragColor = texture(bgTexture, TexCoord);
}