package com.controllerface.trongle.systems.rendering.passes.debug;

import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexArray;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexBuffer;
import com.juncture.alloy.gpu.gl.shaders.GL_Shader;
import com.juncture.alloy.utils.math.Ray3d;
import com.controllerface.trongle.components.Component;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.List;

import static com.juncture.alloy.gpu.Constants.VECTOR_DOUBLE_3D_SIZE;
import static com.juncture.alloy.gpu.Constants.VECTOR_FLOAT_3D_SIZE;
import static org.lwjgl.opengl.GL11C.GL_LINES;
import static org.lwjgl.opengl.GL11C.glDrawArrays;

public class RayCastRenderPass extends RenderPass
{
    private static final int XYZ_ATTRIBUTE = 0;
    private static final int COLOR_ATTRIBUTE = 2;

    private final GL_Shader shader;
    private GL_VertexArray vao;
    private GL_VertexBuffer xyz_vbo;
    private GL_VertexBuffer color_vbo;
    private DoubleBuffer xyz_vbo_buffer;
    private FloatBuffer color_vbo_buffer;

    private int current_vert_count = 0;
    private int max_vert_count = 0;

    private final ECS<Component> ecs;

    public RayCastRenderPass(ECS<Component> ecs)
    {
        this.ecs = ecs;
        shader = GPU.GL.new_shader(resources,"ray_cast");
    }

    private void release_buffers()
    {
        if (vao != null)
        {
            resources.release(vao);

            xyz_vbo.unmap_buffer();
            color_vbo.unmap_buffer();

            resources.release(xyz_vbo);
            resources.release(color_vbo);
        }
    }

    private void create_buffers()
    {
        vao       = GPU.GL.new_vao(resources);
        xyz_vbo   = GPU.GL.dvec3_buffer(resources, vao, XYZ_ATTRIBUTE, current_vert_count * VECTOR_DOUBLE_3D_SIZE);
        color_vbo = GPU.GL.vec3_buffer(resources, vao, COLOR_ATTRIBUTE, current_vert_count * VECTOR_FLOAT_3D_SIZE);

        xyz_vbo_buffer   = xyz_vbo.map_as_double_buffer_persistent();
        color_vbo_buffer = color_vbo.map_as_float_buffer_persistent();

        vao.enable_attribute(XYZ_ATTRIBUTE);
        vao.enable_attribute(COLOR_ATTRIBUTE);
    }

    private void resize_buffers(List<Ray3d> ray_list)
    {
        current_vert_count = ray_list.size() * 2;

        if (current_vert_count > max_vert_count)
        {
            release_buffers();
            create_buffers();
            max_vert_count = current_vert_count;
        }
    }

    private void update_buffers(List<Ray3d> ray_list)
    {
        double[] verts = new double[current_vert_count * 3];
        float[] colors = new float[current_vert_count * 3];

        int current_vert_index = 0;
        for (var ray : ray_list)
        {
            int x1 = current_vert_index++;
            int y1 = current_vert_index++;
            int z1 = current_vert_index++;
            int x2 = current_vert_index++;
            int y2 = current_vert_index++;
            int z2 = current_vert_index++;

            verts[x1] = ray.origin().x;
            verts[y1] = ray.origin().y;
            verts[z1] = ray.origin().z;
            verts[x2] = ray.endpoint().x;
            verts[y2] = ray.endpoint().y;
            verts[z2] = ray.endpoint().z;

            colors[x1] = 0.0f;
            colors[y1] = 1.0f;
            colors[z1] = 0.0f;
            colors[x2] = 1.0f;
            colors[y2] = 0.0f;
            colors[z2] = 0.0f;

        }
        xyz_vbo_buffer.clear();
        xyz_vbo_buffer.put(verts);

        color_vbo_buffer.clear();
        color_vbo_buffer.put(colors);
    }

    private List<Ray3d> get_rays()
    {
        var hull_arrays = ecs.get_components(Component.RayCast);
        return hull_arrays.values().stream()
            .map(Component.RayCast::coerce)
            .map(x -> (Ray3d) x)
            .toList();
    }

    @Override
    public void render()
    {
        var hull_map = get_rays();
        resize_buffers(hull_map);

        if (current_vert_count < 1) return;

        update_buffers(hull_map);

        vao.bind();
        shader.use();
        glDrawArrays(GL_LINES, 0, current_vert_count);
        shader.detach();
        vao.unbind();
    }
}
