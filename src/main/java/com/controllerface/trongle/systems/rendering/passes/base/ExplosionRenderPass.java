package com.controllerface.trongle.systems.rendering.passes.base;

import com.juncture.alloy.data.MutableDouble;
import com.juncture.alloy.data.MutableFloat;
import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexArray;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexBuffer;
import com.juncture.alloy.gpu.gl.shaders.GL_Shader;
import com.juncture.alloy.utils.memory.glsl.Vec2;
import com.juncture.alloy.utils.memory.glsl.Vec4;
import com.controllerface.trongle.components.Component;
import org.joml.Vector3f;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Set;

import static com.juncture.alloy.gpu.Constants.VECTOR_FLOAT_2D_SIZE;
import static com.juncture.alloy.gpu.Constants.VECTOR_FLOAT_4D_SIZE;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL11C.glDepthMask;
import static org.lwjgl.opengl.GL31C.glDrawArraysInstanced;
import static org.lwjgl.opengl.GL45C.glVertexArrayBindingDivisor;

public class ExplosionRenderPass extends RenderPass
{
    private static final int XY_ATTRIBUTE = 0;
    private static final int UV_ATTRIBUTE = 1;
    private static final int LIFE_ATTRIBUTE = 2;
    private static final int TRANSFORM_ATTRIBUTE = 3;

    private static final float[] BILLBOARD_VERTICES =
        {
            -0.5f, -0.5f, // Bottom-left
            0.5f, -0.5f, // Bottom-right
            -0.5f,  0.5f, // Top-left
            0.5f,  0.5f, // Top-right
        };

    private static final float[] BILLBOARD_UVS =
        {
            0.0f, 0.0f, // Bottom-left
            0.0f, 1.0f, // Bottom-right
            1.0f, 0.0f, // Top-left
            1.0f, 1.0f, // Top-right
        };

    private final ECSLayer<Component> ecs;

    private final GL_Shader shader;

    private GL_VertexArray vao;

    private GL_VertexBuffer pos_vbo;
    private GL_VertexBuffer uv_vbo;
    private GL_VertexBuffer life_vbo;
    private GL_VertexBuffer transform_vbo;

    private ByteBuffer life_buffer;
    private ByteBuffer transform_buffer;

    private int max_billboard_count = 0;
    private int billboard_count = 0;

    private Arena memory_arena = Arena.ofConfined();
    private MemorySegment life_segment;
    private MemorySegment transform_segment;

    public ExplosionRenderPass(ECSLayer<Component> ecs)
    {
        this.ecs = ecs;
        shader = GPU.GL.new_shader(resources, "explosion");
    }

    private void release_buffers()
    {
        if (vao != null)
        {
            resources.release(vao);
            resources.release(pos_vbo);
            resources.release(uv_vbo);

            life_vbo.unmap_buffer();
            transform_vbo.unmap_buffer();

            resources.release(life_vbo);
            resources.release(transform_vbo);
        }
    }

    private void create_buffers()
    {
        vao = GPU.GL.new_vao(resources);
        pos_vbo = GPU.GL.vec2_buffer_static(resources, vao, XY_ATTRIBUTE, BILLBOARD_VERTICES);
        uv_vbo = GPU.GL.vec2_buffer_static(resources, vao, UV_ATTRIBUTE, BILLBOARD_UVS);
        life_vbo = GPU.GL.vec2_buffer(resources, vao, LIFE_ATTRIBUTE, max_billboard_count * VECTOR_FLOAT_2D_SIZE);
        transform_vbo = GPU.GL.vec4_buffer(resources, vao, TRANSFORM_ATTRIBUTE, max_billboard_count * VECTOR_FLOAT_4D_SIZE);

        life_buffer = life_vbo.map_as_byte_buffer_persistent();
        transform_buffer = transform_vbo.map_as_byte_buffer_persistent();

        glVertexArrayBindingDivisor(vao.id(), TRANSFORM_ATTRIBUTE, 1);
        glVertexArrayBindingDivisor(vao.id(), LIFE_ATTRIBUTE, 1);

        vao.enable_attribute(XY_ATTRIBUTE);
        vao.enable_attribute(UV_ATTRIBUTE);
        vao.enable_attribute(LIFE_ATTRIBUTE);
        vao.enable_attribute(TRANSFORM_ATTRIBUTE);
    }

    private void resize_off_heap_buffers()
    {
        if (memory_arena != null)
        {
            memory_arena.close();
        }
        memory_arena = Arena.ofConfined();
        life_segment = memory_arena.allocate(Vec2.LAYOUT, billboard_count);
        transform_segment = memory_arena.allocate(Vec4.LAYOUT, billboard_count);
    }

    private void resize_buffers(Set<String> entities)
    {
        billboard_count = entities.size();

        if (billboard_count > max_billboard_count)
        {
            max_billboard_count = billboard_count;
            release_buffers();
            create_buffers();
            resize_off_heap_buffers();
        }
    }

    private void update_buffers(Set<String> entities)
    {
        life_buffer.clear();
        transform_buffer.clear();

        long particle_index = 0;

        for (var particle_entity : entities)
        {
            var position = Component.RenderPosition.<Vector3f>for_entity(ecs, particle_entity);
            var size = Component.BillboardSize.<MutableFloat>for_entity(ecs, particle_entity);
            var life = Component.Lifetime.<MutableDouble>for_entity(ecs, particle_entity);
            var max_life = Component.MaxLifetime.<MutableDouble>for_entity(ecs, particle_entity);

            Vec2.map_at_index(life_segment, particle_index, (float) life.value, (float) max_life.value);
            Vec4.map_at_index(transform_segment, particle_index, position.x, position.y, position.z, size.value);

            particle_index++;
        }

        life_buffer.put(0, life_segment.asByteBuffer(), 0, (int) Vec2.LAYOUT.byteSize() * billboard_count);
        transform_buffer.put(0, transform_segment.asByteBuffer(), 0, (int) Vec4.LAYOUT.byteSize() * billboard_count);
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
        var entities = ecs.get_components(Component.Explosion).keySet();
        resize_buffers(entities);

        if (billboard_count < 1)
        {
            return;
        }

        update_buffers(entities);

        glDepthMask(false);
        vao.bind();
        shader.use();
        glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, billboard_count);
        shader.detach();
        vao.unbind();
        glDepthMask(true);
    }
}
