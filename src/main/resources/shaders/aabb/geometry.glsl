#version 460 core

#layout "ViewData.glsl"

layout (points) in;
layout (line_strip, max_vertices = 24) out;

in vec3 vMin[];
in vec3 vMax[];
in vec3 vColor[];

out vec3 fColor;

void emit_edge(vec3 a, vec3 b)
{
    gl_Position = viewProjection * vec4(a, 1.0);
    fColor = vColor[0];
    EmitVertex();
    gl_Position = viewProjection * vec4(b, 1.0);
    fColor = vColor[0];
    EmitVertex();
    EndPrimitive();
}

void main()
{
    vec3 min = vMin[0];
    vec3 max = vMax[0];

    vec3 v000 = min;
    vec3 v001 = vec3(min.x, min.y, max.z);
    vec3 v010 = vec3(min.x, max.y, min.z);
    vec3 v011 = vec3(min.x, max.y, max.z);
    vec3 v100 = vec3(max.x, min.y, min.z);
    vec3 v101 = vec3(max.x, min.y, max.z);
    vec3 v110 = vec3(max.x, max.y, min.z);
    vec3 v111 = max;

    // Bottom square
    emit_edge(v000, v001);
    emit_edge(v001, v101);
    emit_edge(v101, v100);
    emit_edge(v100, v000);

    // Top square
    emit_edge(v010, v011);
    emit_edge(v011, v111);
    emit_edge(v111, v110);
    emit_edge(v110, v010);

    // Vertical edges
    emit_edge(v000, v010);
    emit_edge(v001, v011);
    emit_edge(v101, v111);
    emit_edge(v100, v110);
}
