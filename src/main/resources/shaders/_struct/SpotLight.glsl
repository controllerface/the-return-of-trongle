struct SpotLight
{
    PointLight point;
    vec4 direction;
    float innerCone;
    float outerCone;
};