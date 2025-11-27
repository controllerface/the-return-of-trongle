#version 460 core

#layout "ViewData.glsl"

layout (location = 0) in vec2 aPos;
layout (location = 1) in vec2 aLife;
layout (location = 2) in vec3 aColor;
layout (location = 3) in vec4 aTransform;

out float blend;
out vec2 fPosition;
out vec3 fColor;

void main()
{
    vec3 billboardPos = aTransform.xyz;
    float size = aTransform.w;
    vec3 right = vec3(view[0][0], view[1][0], view[2][0]) * size;
    vec3 up = vec3(view[0][1], view[1][1], view[2][1]) * size;
    vec3 worldPos = billboardPos + (aPos.x * right) + (aPos.y * up);
    gl_Position = projection * view * vec4(worldPos, 1.0);
    blend = aLife.x / aLife.y;

    float scaledBlend = pow(blend, 0.1);
    blend = scaledBlend;

    fPosition = aPos * 2.0;
    fColor = aColor;
}