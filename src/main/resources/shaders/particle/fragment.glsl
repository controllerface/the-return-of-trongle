#version 460 core

in float blend;
in vec2 fPosition;
in vec3 fColor;

out vec4 FragColor;
void main()
{
    vec4 circleColor = vec4(fColor, blend);
    float fade = 0.05;
    float distance = length(fPosition);
    float alpha = smoothstep(1.0, 1.0 - fade, distance);

    if (distance > 1.0) discard;
    FragColor = circleColor * alpha;
}
