#version 460 core

#include "bias.glsl"

#struct "Light.glsl"
#struct "DirectionalLight.glsl"

#layout "GlobalLight.glsl"

out vec4 FragColor;

in vec3 TexCoords;

uniform samplerCube skybox;
uniform samplerCube skyboxDark;

void main()
{
    float biased = bias(blend, .85);
    vec3 day = textureLod(skybox, TexCoords, 0).rgb;
    vec3 night = textureLod(skyboxDark, TexCoords, 0).rgb;
    FragColor = vec4(mix(day, night, biased), 1.0f);
}