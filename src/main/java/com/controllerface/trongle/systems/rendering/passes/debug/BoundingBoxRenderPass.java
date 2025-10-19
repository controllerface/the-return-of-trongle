package com.controllerface.trongle.systems.rendering.passes.debug;

import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexArray;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexBuffer;
import com.juncture.alloy.gpu.gl.shaders.GL_Shader;
import com.juncture.alloy.physics.bvh.PhysicsNode;
import com.juncture.alloy.physics.bvh.PhysicsTree;
import com.juncture.alloy.utils.math.Bounds3d;
import com.juncture.alloy.utils.math.Bounds3f;
import com.juncture.alloy.utils.math.MutableConvexHull;
import com.controllerface.trongle.components.Component;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.*;

import static com.juncture.alloy.gpu.Constants.VECTOR_DOUBLE_3D_SIZE;
import static com.juncture.alloy.gpu.Constants.VECTOR_FLOAT_3D_SIZE;
import static org.lwjgl.opengl.GL11C.GL_POINTS;
import static org.lwjgl.opengl.GL11C.glDrawArrays;

public class BoundingBoxRenderPass extends RenderPass
{
    private static final int MIN_ATTRIBUTE = 0;
    private static final int MAX_ATTRIBUTE = 2;
    private static final int COLOR_ATTRIBUTE = 4;

    private final GL_Shader shader;
    private GL_VertexArray vao;
    private GL_VertexBuffer min_vbo;
    private GL_VertexBuffer max_vbo;
    private GL_VertexBuffer color_vbo;
    private DoubleBuffer min_vbo_buffer;
    private DoubleBuffer max_vbo_buffer;
    private FloatBuffer color_vbo_buffer;

    private int box_count = 0;

    private final ECS<Component> ecs;

    public BoundingBoxRenderPass(ECS<Component> ecs)
    {
        this.ecs = ecs;
        shader = GPU.GL.new_shader(resources, "aabb");
    }

    private void resize_buffers(Map<String, Object> model_aabbs,
                                Map<String, Object> render_aabbs,
                                List<Bounds3d> mesh_bounds,
                                List<Bounds3d> octree_bounds)
    {
        int current_box_count = model_aabbs.size()
                + render_aabbs.size()
                + mesh_bounds.size()
                + octree_bounds.size();

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
            min_vbo   = GPU.GL.dvec3_buffer(resources, vao, MIN_ATTRIBUTE, current_box_count * VECTOR_DOUBLE_3D_SIZE);
            max_vbo   = GPU.GL.dvec3_buffer(resources, vao, MAX_ATTRIBUTE, current_box_count * VECTOR_DOUBLE_3D_SIZE);
            color_vbo = GPU.GL.vec3_buffer(resources, vao, COLOR_ATTRIBUTE, current_box_count * VECTOR_FLOAT_3D_SIZE);

            min_vbo_buffer   = min_vbo.map_as_double_buffer_persistent();
            max_vbo_buffer   = max_vbo.map_as_double_buffer_persistent();
            color_vbo_buffer = color_vbo.map_as_float_buffer_persistent();

            vao.enable_attribute(MIN_ATTRIBUTE);
            vao.enable_attribute(MAX_ATTRIBUTE);
            vao.enable_attribute(COLOR_ATTRIBUTE);
        }
        box_count = current_box_count;
    }

    private void update_hull_data(Map<String, Object> model_bounds,
                                  Map<String, Object> render_bounds,
                                  List<Bounds3d> mesh_bounds,
                                  List<Bounds3d> octree_bounds)
    {
        double[] min_verts = new double[box_count * 3];
        double[] max_verts = new double[box_count * 3];
        float[] colors = new float[box_count * 3];

        int buffer_index = 0;

        for (var raw_aabb : model_bounds.values())
        {
            Bounds3d bounds = Component.Bounds.coerce(raw_aabb);

            int x = buffer_index++;
            int y = buffer_index++;
            int z = buffer_index++;

            min_verts[x] = bounds.min.x;
            min_verts[y] = bounds.min.y;
            min_verts[z] = bounds.min.z;

            max_verts[x] = bounds.max.x;
            max_verts[y] = bounds.max.y;
            max_verts[z] = bounds.max.z;

            colors[x] = 1.0f;
            colors[y] = 0.0f;
            colors[z] = 0.0f;
        }

        for (var raw_aabb : render_bounds.values())
        {
            Bounds3f bounds = Component.RenderBounds.coerce(raw_aabb);

            int x = buffer_index++;
            int y = buffer_index++;
            int z = buffer_index++;

            min_verts[x] = bounds.min.x;
            min_verts[y] = bounds.min.y;
            min_verts[z] = bounds.min.z;

            max_verts[x] = bounds.max.x;
            max_verts[y] = bounds.max.y;
            max_verts[z] = bounds.max.z;

            colors[x] = 1.0f;
            colors[y] = 0.0f;
            colors[z] = 1.0f;
        }

        for (var aabb : mesh_bounds)
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

            colors[x] = 0.0f;
            colors[y] = 1.0f;
            colors[z] = 0.0f;
        }

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

            colors[x] = 0.5f;
            colors[y] = 0.5f;
            colors[z] = 0.5f;
        }

        min_vbo_buffer.clear();
        max_vbo_buffer.clear();
        color_vbo_buffer.clear();
        min_vbo_buffer.put(min_verts);
        max_vbo_buffer.put(max_verts);
        color_vbo_buffer.put(colors);
    }

    public void collectNodes(List<Bounds3d> nodeList, PhysicsNode node)
    {
        nodeList.add(node.bounds);
        if (node.children != null)
        {
            for (PhysicsNode child : node.children)
            {
                if (child != null)
                {
                    collectNodes(nodeList, child);
                }
            }
        }
    }

    private List<Bounds3d> get_octree_aabbs()
    {
        var octree = Component.CollisionBVH.<PhysicsTree>global_or_null(ecs);
        if (octree == null) return Collections.emptyList();

        List<Bounds3d> bounds = new ArrayList<>();
        bounds.add(octree.bounds);
        collectNodes(bounds, octree.root);
        return bounds;
    }

    private List<Bounds3d> get_mesh_aabbs()
    {
        var hull_arrays = ecs.get_components(Component.Hulls);
        return hull_arrays.values().stream()
            .map(Component.Hulls::coerce)
            .map(x -> ((MutableConvexHull[]) x))
            .flatMap(Arrays::stream)
            .map(h->h.bounds)
            .toList();
    }

    @Override
    public void render()
    {
        var model_aabbs = ecs.get_components(Component.Bounds);
        var render_aabbs = ecs.get_components(Component.RenderBounds);
        var mesh_aabbs = get_mesh_aabbs();
        var octree_aabbs = get_octree_aabbs();
        resize_buffers(model_aabbs, render_aabbs, mesh_aabbs, octree_aabbs);
        box_count = model_aabbs.size() + mesh_aabbs.size() + octree_aabbs.size() + render_aabbs.size();

        if (box_count < 1) return;

        update_hull_data(model_aabbs, render_aabbs, mesh_aabbs, octree_aabbs);
        vao.bind();
        shader.use();
        glDrawArrays(GL_POINTS, 0, box_count);
        shader.detach();
        vao.unbind();
    }
}
