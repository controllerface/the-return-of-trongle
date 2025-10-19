package com.controllerface.trongle.systems.rendering.passes.debug;

import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexArray;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexBuffer;
import com.juncture.alloy.gpu.gl.shaders.GL_Shader;
import com.juncture.alloy.physics.bvh.RenderNode;
import com.juncture.alloy.physics.bvh.RenderTree;
import com.juncture.alloy.utils.math.Bounds3f;
import com.controllerface.trongle.components.Component;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.juncture.alloy.gpu.Constants.VECTOR_FLOAT_3D_SIZE;
import static org.lwjgl.opengl.GL11C.GL_POINTS;
import static org.lwjgl.opengl.GL11C.glDrawArrays;

public class RenderingBoundsRenderPass extends RenderPass
{
    private static final int MIN_ATTRIBUTE = 0;
    private static final int MAX_ATTRIBUTE = 1;
    private static final int COLOR_ATTRIBUTE = 2;

    private final GL_Shader shader;
    private GL_VertexArray vao;
    private GL_VertexBuffer min_vbo;
    private GL_VertexBuffer max_vbo;
    private GL_VertexBuffer color_vbo;
    private FloatBuffer min_vbo_buffer;
    private FloatBuffer max_vbo_buffer;
    private FloatBuffer color_vbo_buffer;

    private int box_count = 0;

    private final ECS<Component> ecs;

    public RenderingBoundsRenderPass(ECS<Component> ecs)
    {
        this.ecs = ecs;
        shader = GPU.GL.new_shader(resources, "render_aabb");
    }

    private void resize_buffers(List<Bounds3f> octree_bounds)
    {
        int current_box_count = octree_bounds.size();

        if (current_box_count > box_count)
        {
            if (vao != null)
            {
                resources.release(vao);
            }
            if (min_vbo != null)
            {
                min_vbo.unmap_buffer();
                resources.release(min_vbo);
            }
            if (max_vbo != null)
            {
                max_vbo.unmap_buffer();
                resources.release(max_vbo);
            }
            if (color_vbo != null)
            {
                color_vbo.unmap_buffer();
                resources.release(color_vbo);
            }

            vao       = GPU.GL.new_vao(resources);
            min_vbo   = GPU.GL.vec3_buffer(resources, vao, MIN_ATTRIBUTE, current_box_count * VECTOR_FLOAT_3D_SIZE);
            max_vbo   = GPU.GL.vec3_buffer(resources, vao, MAX_ATTRIBUTE, current_box_count * VECTOR_FLOAT_3D_SIZE);
            color_vbo = GPU.GL.vec3_buffer(resources, vao, COLOR_ATTRIBUTE, current_box_count * VECTOR_FLOAT_3D_SIZE);

            min_vbo_buffer   = min_vbo.map_as_float_buffer_persistent();
            max_vbo_buffer   = max_vbo.map_as_float_buffer_persistent();
            color_vbo_buffer = color_vbo.map_as_float_buffer_persistent();

            vao.enable_attribute(MIN_ATTRIBUTE);
            vao.enable_attribute(MAX_ATTRIBUTE);
            vao.enable_attribute(COLOR_ATTRIBUTE);
        }
        box_count = current_box_count;
    }

    private void update_hull_data(List<Bounds3f> octree_bounds)
    {
        float[] min_verts = new float[box_count * 3];
        float[] max_verts = new float[box_count * 3];
        float[] colors = new float[box_count * 3];

        int buffer_index = 0;

        for (var aabb : octree_bounds)
        {
            int x = buffer_index++;
            int y = buffer_index++;
            int z = buffer_index++;

            min_verts[x] = aabb.min.x;
            min_verts[y] = aabb.min.y;
            min_verts[z] = aabb.min.z;

            max_verts[x] = aabb.max.x;
            max_verts[y] = aabb.max.y;
            max_verts[z] = aabb.max.z;

            colors[x] = 0.9f;
            colors[y] = 0.1f;
            colors[z] = 0.5f;
        }

        min_vbo_buffer.clear();
        max_vbo_buffer.clear();
        color_vbo_buffer.clear();
        min_vbo_buffer.put(min_verts);
        max_vbo_buffer.put(max_verts);
        color_vbo_buffer.put(colors);
    }

    public void collectNodes(List<Bounds3f> nodeList, RenderNode node)
    {
        nodeList.add(node.bounds);
        if (node.children != null)
        {
            for (RenderNode child : node.children)
            {
                if (child != null)
                {
                    collectNodes(nodeList, child);
                }
            }
        }
    }

    private List<Bounds3f> get_octree_aabbs()
    {
        var octree = Component.RenderBVH.<RenderTree>global_or_null(ecs);
        if (octree == null) return Collections.emptyList();

        List<Bounds3f> bounds = new ArrayList<>();
        bounds.add(octree.bounds);
        collectNodes(bounds, octree.root);
        return bounds;
    }

    @Override
    public void render()
    {
        var octree_aabbs = get_octree_aabbs();
        resize_buffers(octree_aabbs);
        box_count = octree_aabbs.size();

        if (box_count < 1) return;

        update_hull_data(octree_aabbs);
        vao.bind();
        shader.use();
        glDrawArrays(GL_POINTS, 0, box_count);
        shader.detach();
        vao.unbind();
    }
}
