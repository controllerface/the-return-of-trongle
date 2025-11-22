package com.controllerface.trongle.systems.rendering.passes.ui;

import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.TextGlyph;
import com.juncture.alloy.gpu.Window;
import com.juncture.alloy.gpu.gl.buffers.GL_CommandBuffer;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexArray;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexBuffer;
import com.juncture.alloy.gpu.gl.shaders.GL_Shader;
import com.juncture.alloy.gpu.gl.textures.GL_TextureArray;
import com.juncture.alloy.rendering.RenderComponent;
import com.juncture.alloy.ui.UITemplate;
import com.juncture.alloy.utils.memory.glsl.Float;
import com.juncture.alloy.utils.memory.glsl.Vec2;
import com.juncture.alloy.utils.memory.opengl.DrawArraysIndirectCommand;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static com.juncture.alloy.gpu.Constants.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL43C.glMultiDrawArraysIndirect;

public class UITextPass extends RenderPass
{
    private static final int XY_ATTRIBUTE = 0;
    private static final int UV_ATTRIBUTE = 1;
    private static final int ID_ATTRIBUTE = 2;

    private static final int VERTICES_PER_LETTER = 4;
    private static final int QUAD_INSTANCE_COUNT = 1;
    private static final int TEXTURE_SIZE = 64;

    private final Map<Character, TextGlyph> character_map = new HashMap<>();
    private final GL_TextureArray font_texture;

    private final UITemplate ui_template;
    private final Window window;

    private final GL_VertexArray vao;
    private final GL_Shader shader;

    private GL_CommandBuffer cbo;
    private GL_VertexBuffer xy_vbo;
    private GL_VertexBuffer uv_vbo;
    private GL_VertexBuffer id_vbo;

    private ByteBuffer cbo_buffer;
    private ByteBuffer xy_buffer;
    private ByteBuffer uv_buffer;
    private ByteBuffer id_buffer;

    private MemorySegment cbo_segment;
    private MemorySegment xy_segment;
    private MemorySegment uv_segment;
    private MemorySegment id_segment;

    private Arena memory_arena = Arena.ofConfined();

    private int max_glyph_count;
    private int glyph_count;

    private boolean dirty = true;

    private float max_char_height = 0;

    public UITextPass(ECSLayer<RenderComponent> recs, UITemplate ui_template)
    {
        this.window = RenderComponent.MainWindow.global(recs);
        this.ui_template = ui_template;
        this.glyph_count = this.ui_template.getCharacter_count();

        font_texture = GPU.GL.build_character_map(resources, TEXTURE_SIZE, "/font/Inconsolata-Light.ttf", character_map);
        shader = GPU.GL.new_shader(resources, "text");
        vao = GPU.GL.new_vao(resources);

        gather_text_metrics();
        rebuild_ui();
    }

    public void mark_rebuild()
    {
        dirty = true;
    }

    private void gather_text_metrics()
    {
        for (var character : character_map.values())
        {
            max_char_height = Math.max(max_char_height, character.size()[1]);
        }
    }

    private void build_cmd()
    {
        cbo_buffer.clear();
        for (int i = 0; i < max_glyph_count; i++)
        {
            int index = i * VERTICES_PER_LETTER;
            DrawArraysIndirectCommand.map_at_index(cbo_segment, i, VERTICES_PER_LETTER, QUAD_INSTANCE_COUNT, index, i);
        }
        cbo_buffer.put(cbo_segment.asByteBuffer());
    }

    private void resize_buffers()
    {
        max_glyph_count = glyph_count;
        if (xy_vbo != null)
        {
            xy_vbo.unmap_buffer();
            resources.release(xy_vbo);
        }
        if (uv_vbo != null)
        {
            uv_vbo.unmap_buffer();
            resources.release(uv_vbo);
        }
        if (id_vbo != null)
        {
            id_vbo.unmap_buffer();
            resources.release(id_vbo);
        }
        if (cbo != null)
        {
            cbo.unmap_buffer();
            resources.release(cbo);
        }

        var command_buffer_size = DrawArraysIndirectCommand.calculate_buffer_size(max_glyph_count);

        xy_vbo = GPU.GL.vec2_buffer(resources, vao, XY_ATTRIBUTE, VECTOR_FLOAT_2D_SIZE * VERTICES_PER_LETTER * max_glyph_count);
        uv_vbo = GPU.GL.vec2_buffer(resources, vao, UV_ATTRIBUTE, VECTOR_FLOAT_2D_SIZE * VERTICES_PER_LETTER * max_glyph_count);
        id_vbo = GPU.GL.float_buffer(resources, vao, ID_ATTRIBUTE, SCALAR_FLOAT_SIZE * max_glyph_count);
        cbo = GPU.GL.command_buffer(resources, command_buffer_size);

        xy_buffer = xy_vbo.map_as_byte_buffer_persistent();
        uv_buffer = uv_vbo.map_as_byte_buffer_persistent();
        id_buffer = id_vbo.map_as_byte_buffer_persistent();
        cbo_buffer = cbo.map_as_byte_buffer_persistent();

        vao.enable_attribute(XY_ATTRIBUTE);
        vao.enable_attribute(UV_ATTRIBUTE);
        vao.enable_attribute(ID_ATTRIBUTE);
        vao.instance_attribute(ID_ATTRIBUTE, 1);

        memory_arena.close();
        memory_arena = Arena.ofConfined();

        xy_segment = memory_arena.allocate(Vec2.LAYOUT, (long) VERTICES_PER_LETTER * max_glyph_count);
        uv_segment = memory_arena.allocate(Vec2.LAYOUT, (long) VERTICES_PER_LETTER * max_glyph_count);
        id_segment = memory_arena.allocate(Float.LAYOUT, glyph_count);
        cbo_segment = memory_arena.allocate(DrawArraysIndirectCommand.LAYOUT, max_glyph_count);
    }

    private void update_glyphs()
    {
        xy_buffer.clear();
        uv_buffer.clear();
        id_buffer.clear();

        int xy_offset = 0;
        int uv_offset = 0;
        int id_offset = 0;

        var xy_buf = new float[VECTOR_2D_LENGTH * 4];

        for (var text_quad : ui_template.text_quads())
        {
            text_quad.dimensions().gl_convert(xy_buf, window.height());
            float x = xy_buf[0];
            float y = xy_buf[1];
            float scale = text_quad.dimensions().h() / TEXTURE_SIZE;

            for (var character : text_quad.text().toCharArray())
            {
                var glyph = character_map.get(character);

                float w = glyph.size()[0] * scale;
                float h = glyph.size()[1] * scale;
                float x1 = x + glyph.bearing()[0] * scale;
                float y1 = y - (glyph.size()[1] - glyph.bearing()[1]) * scale;
                float x2 = x1 + w;
                float y2 = y1 + h;
                float u1 = 0.0f;
                float v1 = 0.0f;
                float u2 = (float) glyph.size()[0] / TEXTURE_SIZE;
                float v2 = (float) glyph.size()[1] / TEXTURE_SIZE;

                Vec2.map_at_index(xy_segment, xy_offset++, x1, y1);
                Vec2.map_at_index(xy_segment, xy_offset++, x2, y1);
                Vec2.map_at_index(xy_segment, xy_offset++, x1, y2);
                Vec2.map_at_index(xy_segment, xy_offset++, x2, y2);

                Vec2.map_at_index(uv_segment, uv_offset++, u1, v1);
                Vec2.map_at_index(uv_segment, uv_offset++, u2, v1);
                Vec2.map_at_index(uv_segment, uv_offset++, u1, v2);
                Vec2.map_at_index(uv_segment, uv_offset++, u2, v2);

                Float.map_at_index(id_segment, id_offset++, glyph.texture_id());
                x += (glyph.advance() >> 6) * scale;
            }
        }

        xy_buffer.put(xy_segment.asByteBuffer());
        uv_buffer.put(uv_segment.asByteBuffer());
        id_buffer.put(id_segment.asByteBuffer());
    }

    private void rebuild_ui()
    {
        if (glyph_count > max_glyph_count)
        {
            resize_buffers();
        }
        build_cmd();
        update_glyphs();
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
            glyph_count = ui_template.getCharacter_count();
            rebuild_ui();
        }

        glDepthFunc(GL_LEQUAL);
        vao.bind();
        shader.use();
        font_texture.bind(0);
        cbo.bind();
        glMultiDrawArraysIndirect(GL_TRIANGLE_STRIP, 0, glyph_count, 0);
        vao.unbind();
        shader.detach();
        glDepthFunc(GL_LESS);
    }
}
