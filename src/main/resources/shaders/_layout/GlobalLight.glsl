layout(std140, binding = 1) uniform GlobalLight
{
    DirectionalLight sun;
    DirectionalLight moon;
    float blend;
    int point_light_count;
    int spot_light_count;
};
