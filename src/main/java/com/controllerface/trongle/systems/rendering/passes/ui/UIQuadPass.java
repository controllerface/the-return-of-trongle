package com.controllerface.trongle.systems.rendering.passes.ui;

import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.Window;
import com.juncture.alloy.gpu.gl.buffers.GL_CommandBuffer;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexArray;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexBuffer;
import com.juncture.alloy.gpu.gl.shaders.GL_Shader;
import com.juncture.alloy.rendering.RenderComponent;
import com.juncture.alloy.ui.UITemplate;
import com.juncture.alloy.utils.memory.glsl.Vec2;
import com.juncture.alloy.utils.memory.glsl.Vec4;
import com.juncture.alloy.utils.memory.opengl.DrawArraysIndirectCommand;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import static com.juncture.alloy.gpu.Constants.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL43C.glMultiDrawArraysIndirect;

public class UIQuadPass extends RenderPass
{
    private static final int XY_ATTRIBUTE = 0;
    private static final int COLOR_ATTRIBUTE = 1;

    private static final int VERTICES_PER_QUAD   = 4;
    private static final int QUAD_INSTANCE_COUNT = 1;

    private final Window window;
    private final UITemplate ui_template;

    private final GL_VertexArray vao;
    private final GL_Shader shader;

    // todo: command buffer should resize as well when since the backing document could have elements added
    //  at runtime
    private final GL_CommandBuffer cbo;

    private GL_VertexBuffer xy_vbo;
    private GL_VertexBuffer color_vbo;

    private ByteBuffer xy_buffer;
    private ByteBuffer color_buffer;

    private MemorySegment xy_segment;
    private MemorySegment color_segment;

    private Arena memory_arena = Arena.ofConfined();

    private int max_quad_count;
    private int quad_count;

    private boolean dirty  = true;

    public UIQuadPass(ECSLayer<RenderComponent> recs, UITemplate ui_template)
    {
        this.ui_template = ui_template;
        this.window = RenderComponent.MainWindow.global(recs);
        this.quad_count = this.ui_template.ui_quads().size();

        var command_buffer_size = DrawArraysIndirectCommand.calculate_buffer_size(quad_count);
        shader = GPU.GL.new_shader(resources, "ui");
        vao = GPU.GL.new_vao(resources);
        cbo = GPU.GL.command_buffer(resources, command_buffer_size);

        build_cmd();
        rebuild_ui();
    }

    public void mark_rebuild()
    {
        dirty = true;
    }

    private void build_cmd()
    {
        int quad_count = ui_template.ui_quads().size();
        var buffer = cbo.map_as_byte_buffer();
        buffer.clear();
        try (var arena = Arena.ofConfined())
        {
            var segment = arena.allocate(DrawArraysIndirectCommand.LAYOUT, quad_count);
            for (int i = 0; i < quad_count; i++)
            {
                int index = i * VERTICES_PER_QUAD;
                DrawArraysIndirectCommand.map_at_index(segment, i, VERTICES_PER_QUAD, QUAD_INSTANCE_COUNT, index, i);
            }
            buffer.put(segment.asByteBuffer());
        }
        cbo.unmap_buffer();
    }

    private void resize_buffers()
    {
        max_quad_count = quad_count;
        if (xy_vbo != null)
        {
            xy_vbo.unmap_buffer();
            resources.release(xy_vbo);
        }
        if (color_vbo != null)
        {
            color_vbo.unmap_buffer();
            resources.release(color_vbo);
        }

        xy_vbo    = GPU.GL.vec2_buffer(resources, vao, XY_ATTRIBUTE, VECTOR_FLOAT_2D_SIZE * max_quad_count * VERTICES_PER_QUAD);
        color_vbo = GPU.GL.vec4_buffer(resources, vao, COLOR_ATTRIBUTE, VECTOR_FLOAT_4D_SIZE  * max_quad_count);

        xy_buffer    = xy_vbo.map_as_byte_buffer_persistent();
        color_buffer = color_vbo.map_as_byte_buffer_persistent();

        vao.instance_attribute(COLOR_ATTRIBUTE, 1);

        vao.enable_attribute(XY_ATTRIBUTE);
        vao.enable_attribute(COLOR_ATTRIBUTE);

        memory_arena.close();
        memory_arena = Arena.ofConfined();

        xy_segment    = memory_arena.allocate(Vec2.LAYOUT, (long) VERTICES_PER_QUAD * max_quad_count );
        color_segment = memory_arena.allocate(Vec4.LAYOUT, max_quad_count);
    }

    private void rebuild_ui()
    {
        if (quad_count > max_quad_count)
        {
            resize_buffers();
        }

        var xy_buf = new float[VECTOR_2D_LENGTH * 4];
        var clr_buf = new float[VECTOR_4D_LENGTH];

        int xy_offset = 0;
        int clr_offset = 0;

        for (var quad : ui_template.ui_quads())
        {
            quad.gl_convert(xy_buf, clr_buf, window.height());
            Vec2.map_at_index(xy_segment, xy_offset++, xy_buf[0], xy_buf[1]);
            Vec2.map_at_index(xy_segment, xy_offset++, xy_buf[2], xy_buf[3]);
            Vec2.map_at_index(xy_segment, xy_offset++, xy_buf[4], xy_buf[5]);
            Vec2.map_at_index(xy_segment, xy_offset++, xy_buf[6], xy_buf[7]);
            Vec4.map_at_index(color_segment, clr_offset++, clr_buf[0], clr_buf[1], clr_buf[2], clr_buf[3]);
        }

        xy_buffer.clear();
        color_buffer.clear();

        xy_buffer.put(xy_segment.asByteBuffer());
        color_buffer.put(color_segment.asByteBuffer());

        dirty = false;
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
        if (dirty)
        {
            quad_count = ui_template.ui_quads().size();
            rebuild_ui();
        }

        glDepthFunc(GL_LEQUAL);
        vao.bind();
        shader.use();
        cbo.bind();
        glMultiDrawArraysIndirect(GL_TRIANGLE_STRIP, 0, quad_count, 0);
        vao.unbind();
        shader.detach();
        glDepthFunc(GL_LESS);
    }
}
