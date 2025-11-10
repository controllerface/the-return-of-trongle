package com.controllerface.trongle.systems.rendering.passes.debug;

import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.gl.buffers.GL_ElementBuffer;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexArray;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexBuffer;
import com.juncture.alloy.gpu.gl.shaders.GL_Shader;
import com.juncture.alloy.utils.math.MutableConvexHull;
import com.controllerface.trongle.components.Component;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

import static com.juncture.alloy.gpu.Constants.*;
import static org.lwjgl.opengl.GL11C.*;

public class ConvexHullRenderPass extends RenderPass
{
    private static final int XYZ_ATTRIBUTE = 0;
    private static final int COLOR_ATTRIBUTE = 2;

    private final GL_Shader shader;
    private GL_VertexArray vao;
    private GL_VertexBuffer xyz_vbo;
    private GL_VertexBuffer color_vbo;
    private GL_ElementBuffer ebo;
    private DoubleBuffer xyz_vbo_buffer;
    private FloatBuffer color_vbo_buffer;
    private IntBuffer ebo_buffer;

    private int vert_count = 0;
    private int face_count = 0;

    private final ECSLayer<Component> ecs;

    public ConvexHullRenderPass(ECSLayer<Component> ecs)
    {
        this.ecs = ecs;
        shader = GPU.GL.new_shader(resources,"convex_hull");
    }

    private void resize_buffers(List<MutableConvexHull[]> hull_map)
    {
        int current_vert_count = 0;
        int current_face_count = 0;
        for (var hulls : hull_map)
        {
            for (var hull : hulls)
            {
                current_vert_count += hull.vertices.length;
                current_face_count += hull.faces.size();
            }
        }
        if (current_vert_count > vert_count || current_face_count > face_count)
        {
            if (vao != null)
            {
                resources.release(vao);
            }
            vao = GPU.GL.new_vao(resources);
        }
        if (current_vert_count > vert_count)
        {
            if (xyz_vbo != null)
            {
                xyz_vbo.unmap_buffer();
                resources.release(xyz_vbo);
            }
            if (color_vbo != null)
            {
                color_vbo.unmap_buffer();
                resources.release(color_vbo);
            }
            xyz_vbo = GPU.GL.dvec3_buffer(resources, vao, XYZ_ATTRIBUTE, current_vert_count * VECTOR_DOUBLE_3D_SIZE);
            color_vbo = GPU.GL.vec3_buffer(resources, vao, COLOR_ATTRIBUTE, current_vert_count * VECTOR_FLOAT_3D_SIZE);
            xyz_vbo_buffer = xyz_vbo.map_as_double_buffer_persistent();
            color_vbo_buffer = color_vbo.map_as_float_buffer_persistent();
            vao.enable_attribute(XYZ_ATTRIBUTE);
            vao.enable_attribute(COLOR_ATTRIBUTE);
        }
        if (current_face_count > face_count)
        {
            if (ebo != null)
            {
                ebo.unmap_buffer();
                resources.release(ebo);
            }
            ebo = GPU.GL.element_buffer(resources, vao, (long) current_face_count * 3 * SCALAR_INT_SIZE);
            ebo_buffer = ebo.map_as_int_buffer_persistent();
        }
        vert_count = current_vert_count;
        face_count = current_face_count;
    }

    private void update_hull_data(List< MutableConvexHull[]> hull_map)
    {
        double[] verts = new double[vert_count * 3];
        float[] colors = new float[vert_count * 3];
        int[] faces = new int[face_count * 3];
        int current_vert_offset = 0;
        int current_vert_index = 0;
        int current_face_index = 0;
        for (var hulls : hull_map)
        {
            for (var hull : hulls)
            {
                float r = 0.1f;
                float g = 0.3f;
                float b = 0.5f;
                for (var vertex : hull.vertices)
                {
                    int x = current_vert_index++;
                    int y = current_vert_index++;
                    int z = current_vert_index++;

                    verts[x] = vertex.x;
                    verts[y] = vertex.y;
                    verts[z] = vertex.z;

                    colors[x] = r;
                    colors[y] = g;
                    colors[z] = b;
                }
                for (var face : hull.faces)
                {
                    faces[current_face_index++] = face.p0() + current_vert_offset;
                    faces[current_face_index++] = face.p1() + current_vert_offset;
                    faces[current_face_index++] = face.p2() + current_vert_offset;
                }
                current_vert_offset += hull.vertices.length;
            }
        }
        xyz_vbo_buffer.clear();
        xyz_vbo_buffer.put(verts);
        color_vbo_buffer.clear();
        color_vbo_buffer.put(colors);
        ebo_buffer.clear();
        ebo_buffer.put(faces);
    }

    private List<MutableConvexHull[]> get_mesh_aabbs()
    {
        var hull_arrays = ecs.get_components(Component.Hulls);
        return hull_arrays.values().stream()
            .map(Component.Hulls::coerce)
            .map(x -> (MutableConvexHull[]) x)
            .toList();
    }

    @Override
    public void render()
    {
        var hull_map = get_mesh_aabbs();
        resize_buffers(hull_map);
        if (face_count < 1) return;
        update_hull_data(hull_map);

        glDisable(GL_DEPTH_TEST);
        vao.bind();
        shader.use();
        glDrawElements(GL_TRIANGLES, face_count * 3, GL_UNSIGNED_INT, 0);
        shader.detach();
        vao.unbind();
        glEnable(GL_DEPTH_TEST);
    }
}
