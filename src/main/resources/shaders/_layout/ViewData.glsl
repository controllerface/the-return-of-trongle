layout(std140, binding = 0) uniform ViewData
{
    mat4 view;
    mat4 projection;
    mat4 screenProjection;
    mat4 viewProjection;
    mat4 skyboxView;
    vec3 viewPosition;
};
