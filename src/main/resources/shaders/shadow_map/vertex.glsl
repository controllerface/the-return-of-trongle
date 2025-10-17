#version 460 core

#layout "ViewData.glsl"

layout (location = 0) in vec3 aPos;
layout (location = 7) in ivec2 transformId;

uniform mat4 lightSpaceMatrix;

layout(std430, binding = 2) readonly buffer MeshMatrices
{
    mat4 mesh_transforms[];
};

layout(std430, binding = 3) readonly buffer ModelMatrices
{
    mat4 model_transforms[];
};

void main()
{
    mat4 mesh_transform = mesh_transforms[transformId.x];
    mat4 model_transform = model_transforms[transformId.y];
    mat4 world = model_transform * mesh_transform;
    gl_Position = lightSpaceMatrix * world * vec4(aPos, 1.0);
}
