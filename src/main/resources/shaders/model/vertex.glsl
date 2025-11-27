#version 460 core

#struct "Light.glsl"
#struct "DirectionalLight.glsl"

#layout "ViewData.glsl"

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoords;
layout (location = 2) in vec3 aNormal;
layout (location = 3) in vec3 aTangent;
layout (location = 4) in vec3 aBitangent;
layout (location = 5) in vec4 aColor;
layout (location = 6) in float materialId;
layout (location = 7) in ivec2 transformId;

uniform mat4 lightSpaceMatrix;

out VERT_DATA
{
    vec3 FragPos;
    vec2 TexCoords;
} vert_out;

flat out float tindex;

out vec4 vColor;
out mat3 TBN;
out vec4 fragPosLightSpace;

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
    tindex = materialId;

    mat4 mesh_transform = mesh_transforms[transformId.x];
    mat4 model_transform = model_transforms[transformId.y];
    mat4 world = model_transform * mesh_transform;

    fragPosLightSpace = lightSpaceMatrix * world * vec4(aPos, 1.0);

    vert_out.FragPos = (world * vec4(aPos, 1.0)).xyz;
    vert_out.TexCoords = aTexCoords;
    vColor = aColor;

    // transpose inverse needed only if non-uniform scaling is supported
    mat3 normalMatrix = mat3(world);
    //normalMatrix = transpose(inverse(normalMatrix));

    vec3 T = normalize(normalMatrix * aTangent);
    vec3 B = normalize(normalMatrix * aBitangent);
    vec3 N = normalize(normalMatrix * aNormal);

    TBN = (mat3(T, B, N));

    gl_Position = viewProjection * world * vec4(aPos, 1.0);
}
