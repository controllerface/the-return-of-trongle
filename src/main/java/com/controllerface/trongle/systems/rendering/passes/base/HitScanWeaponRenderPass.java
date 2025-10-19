package com.controllerface.trongle.systems.rendering.passes.base;

import com.juncture.alloy.data.MutableDouble;
import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.gl.buffers.GL_CommandBuffer;
import com.juncture.alloy.gpu.gl.buffers.GL_ElementBuffer;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexArray;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexBuffer;
import com.juncture.alloy.gpu.gl.shaders.GL_Shader;
import com.juncture.alloy.utils.math.Quad3d;
import com.juncture.alloy.utils.memory.glsl.Vec3;
import com.juncture.alloy.utils.memory.opengl.DrawElementsIndirectCommand;
import com.controllerface.trongle.components.Component;
import org.joml.Vector3f;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.Set;

import static com.juncture.alloy.gpu.Constants.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL43C.glMultiDrawElementsIndirect;
import static org.lwjgl.opengl.GL45C.glVertexArrayBindingDivisor;

public class HitScanWeaponRenderPass extends RenderPass
{
    private static final int RAY_INDEX_COUNT = 4;
    private static final int RAY_INSTANCE_COUNT = 1;
    private static final int RAY_INDEX_OFFSET = 0;

    private static final int XYZ_ATTRIBUTE = 0;
    private static final int COLOR_ATTRIBUTE = 1;
    private static final int BLEND_ATTRIBUTE = 2;

    private final GL_Shader shader;
    private GL_VertexArray vao;
    private GL_ElementBuffer ebo;
    private GL_CommandBuffer cbo;
    private GL_VertexBuffer xyz_vbo;
    private GL_VertexBuffer color_vbo;
    private GL_VertexBuffer blend_vbo;

    private ByteBuffer command_buffer;
    private ByteBuffer ebo_buffer;
    private ByteBuffer xyz_vbo_buffer;
    private ByteBuffer color_vbo_buffer;
    private ByteBuffer blend_vbo_buffer;

    private int vert_count = 0;
    private int ray_count = 0;
    private int max_vert_count = 0;

    private final ECS<Component> ecs;

    private Arena memory_arena = Arena.ofConfined();
    private MemorySegment cmd_segment;
    private MemorySegment index_segment;
    private MemorySegment blend_segment;
    private MemorySegment color_segment;
    private MemorySegment pos_segment;

    public HitScanWeaponRenderPass(ECS<Component> ecs)
    {
        this.ecs = ecs;
        shader = GPU.GL.new_shader(resources, "hit_scan_weapon");
    }

    private void release_buffers()
    {
        if (vao != null)
        {
            resources.release(vao);

            ebo.unmap_buffer();
            cbo.unmap_buffer();
            xyz_vbo.unmap_buffer();
            blend_vbo.unmap_buffer();
            color_vbo.unmap_buffer();

            resources.release(ebo);
            resources.release(cbo);
            resources.release(xyz_vbo);
            resources.release(blend_vbo);
            resources.release(color_vbo);
        }
    }

    private void create_buffers()
    {
        vao = GPU.GL.new_vao(resources);
        ebo = GPU.GL.element_buffer(resources, vao, (long) vert_count * SCALAR_INT_SIZE);
        cbo = GPU.GL.command_buffer(resources, DrawElementsIndirectCommand.calculate_buffer_size(ray_count));
        xyz_vbo = GPU.GL.vec3_buffer(resources, vao, XYZ_ATTRIBUTE, vert_count * VECTOR_FLOAT_3D_SIZE);
        color_vbo = GPU.GL.vec3_buffer(resources, vao, COLOR_ATTRIBUTE, vert_count * VECTOR_FLOAT_3D_SIZE);
        blend_vbo = GPU.GL.float_buffer(resources, vao, BLEND_ATTRIBUTE, ray_count * SCALAR_FLOAT_SIZE);

        ebo_buffer = ebo.map_as_byte_buffer_persistent();
        command_buffer = cbo.map_as_byte_buffer_persistent();
        xyz_vbo_buffer = xyz_vbo.map_as_byte_buffer_persistent();
        color_vbo_buffer = color_vbo.map_as_byte_buffer_persistent();
        blend_vbo_buffer = blend_vbo.map_as_byte_buffer_persistent();

        glVertexArrayBindingDivisor(vao.id(), BLEND_ATTRIBUTE, 1);

        vao.enable_attribute(XYZ_ATTRIBUTE);
        vao.enable_attribute(COLOR_ATTRIBUTE);
        vao.enable_attribute(BLEND_ATTRIBUTE);
    }

    private void resize_off_heap_buffers()
    {
        if (memory_arena != null)
        {
            memory_arena.close();
        }
        memory_arena = Arena.ofConfined();
        cmd_segment = memory_arena.allocate(DrawElementsIndirectCommand.LAYOUT, ray_count);
        index_segment = memory_arena.allocate(ValueLayout.JAVA_INT, vert_count);
        blend_segment = memory_arena.allocate(ValueLayout.JAVA_FLOAT, ray_count);
        color_segment = memory_arena.allocate(Vec3.LAYOUT, vert_count);
        pos_segment = memory_arena.allocate(Vec3.LAYOUT, vert_count);
    }

    private void resize_buffers(Set<String> entities)
    {
        ray_count = entities.size();
        vert_count = ray_count * 4;

        if (vert_count > max_vert_count)
        {
            release_buffers();
            create_buffers();
            resize_off_heap_buffers();
            max_vert_count = vert_count;
        }
    }

    private void update_buffers(Set<String> entities)
    {
        command_buffer.clear();
        ebo_buffer.clear();
        xyz_vbo_buffer.clear();
        color_vbo_buffer.clear();
        blend_vbo_buffer.clear();

        long vert_index = 0;
        long ray_index = 0;
        for (var ray_entity : entities)
        {
            var life = Component.Lifetime.<MutableDouble>for_entity(ecs, ray_entity);
            var max_life = Component.MaxLifetime.<MutableDouble>for_entity(ecs, ray_entity);
            var tip_color = Component.TrailTipColor.<Vector3f>for_entity(ecs, ray_entity);
            var tail_color = Component.TrailTailColor.<Vector3f>for_entity(ecs, ray_entity);
            var quad_r = Component.HitScanTrailRender.<Quad3d>for_entity(ecs, ray_entity);

            float blend = (float) (life.value / max_life.value);

            long v0 = vert_index++;
            long v1 = vert_index++;
            long v2 = vert_index++;
            long v3 = vert_index++;

            long v0_index = v0 * ValueLayout.JAVA_INT.byteSize();
            long v1_index = v1 * ValueLayout.JAVA_INT.byteSize();
            long v2_index = v2 * ValueLayout.JAVA_INT.byteSize();
            long v3_index = v3 * ValueLayout.JAVA_INT.byteSize();

            long blend_index = ray_index * ValueLayout.JAVA_FLOAT.byteSize();

            int base_vertex = (int) (ray_index * RAY_INDEX_COUNT);

            DrawElementsIndirectCommand.map_at_index(cmd_segment,
                ray_index,
                RAY_INDEX_COUNT,
                RAY_INSTANCE_COUNT,
                RAY_INDEX_OFFSET,
                base_vertex,
                (int) ray_index);

            Vec3.map_at_index(pos_segment, v0, quad_r.tail_left());
            Vec3.map_at_index(pos_segment, v1, quad_r.tail_right());
            Vec3.map_at_index(pos_segment, v2, quad_r.tip_left());
            Vec3.map_at_index(pos_segment, v3, quad_r.tip_right());

            Vec3.map_at_index(color_segment, v0, tail_color);
            Vec3.map_at_index(color_segment, v1, tail_color);
            Vec3.map_at_index(color_segment, v2, tip_color);
            Vec3.map_at_index(color_segment, v3, tip_color);

            index_segment.set(ValueLayout.JAVA_INT, v0_index, 0);
            index_segment.set(ValueLayout.JAVA_INT, v1_index, 1);
            index_segment.set(ValueLayout.JAVA_INT, v2_index, 2);
            index_segment.set(ValueLayout.JAVA_INT, v3_index, 3);

            blend_segment.set(ValueLayout.JAVA_FLOAT, blend_index, blend);
            ray_index++;
        }

        command_buffer.put(0, cmd_segment.asByteBuffer(), 0, (int) DrawElementsIndirectCommand.LAYOUT.byteSize() * ray_count);
        ebo_buffer.put(0, index_segment.asByteBuffer(), 0, (int) ValueLayout.JAVA_INT.byteSize() * vert_count);
        xyz_vbo_buffer.put(0, pos_segment.asByteBuffer(), 0, (int) Vec3.LAYOUT.byteSize() * vert_count);
        color_vbo_buffer.put(0, color_segment.asByteBuffer(), 0, (int) Vec3.LAYOUT.byteSize() * vert_count);
        blend_vbo_buffer.put(0, blend_segment.asByteBuffer(), 0, (int) ValueLayout.JAVA_FLOAT.byteSize() * ray_count);
    }

    @Override
    public void release()
    {
        super.release();
        memory_arena.close();
    }

    @Override
    public void render()
    {
        var entities = ecs.get_components(Component.HitScanWeapon).keySet();
        resize_buffers(entities);

        if (ray_count < 1)
        {
            return;
        }

        glDisable(GL_CULL_FACE);
        update_buffers(entities);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        vao.bind();
        cbo.bind();
        shader.use();
        glMultiDrawElementsIndirect(GL_TRIANGLE_STRIP, GL_UNSIGNED_INT, 0, ray_count, 0);
        shader.detach();
        vao.unbind();
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_CULL_FACE);
    }
}
