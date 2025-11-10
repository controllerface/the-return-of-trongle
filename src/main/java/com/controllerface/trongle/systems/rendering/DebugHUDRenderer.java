package com.controllerface.trongle.systems.rendering;

import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.events.CoreEvent;
import com.juncture.alloy.events.Event;
import com.juncture.alloy.events.EventBus;
import com.juncture.alloy.events.MessageEvent;
import com.juncture.alloy.events.debug.DebugEvent;
import com.juncture.alloy.events.debug.PositionEvent;
import com.juncture.alloy.events.debug.ViewDebugEvent;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.TextGlyph;
import com.juncture.alloy.gpu.Window;
import com.juncture.alloy.gpu.gl.buffers.GL_CommandBuffer;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexArray;
import com.juncture.alloy.gpu.gl.shaders.GL_Shader;
import com.juncture.alloy.gpu.gl.textures.GL_TextureArray;
import com.controllerface.trongle.components.Component;
import com.controllerface.trongle.systems.rendering.hud.SnapPosition;
import com.controllerface.trongle.systems.rendering.hud.TextContainer;

import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

import static com.juncture.alloy.gpu.Constants.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL43C.glMultiDrawArraysIndirect;

public class DebugHUDRenderer extends RenderPass
{
    // todo: Currently, this renderer doesn't properly batch rendering calls, essentially it creates one batch
    //  worth of space in the command buffer, and draws that, assuming the amount of text to draw will never be
    //  more than will fit into the buffer. This is obviously not a long-term solution, so at some point we will
    //  need to add logic to determine how many batches are actually required, and render them accordingly.

    private static final int VERTICES_PER_LETTER = 4;
    private static final int COMMAND_BUFFER_SIZE = RENDER_BATCH_SIZE * Integer.BYTES * 4;
    private final int[] raw_cmd = new int[VERTICES_PER_LETTER * RENDER_BATCH_SIZE];

    private static final int TEXTURE_SIZE = 64;

    private static final int XY_ATTRIBUTE = 0;
    private static final int UV_ATTRIBUTE = 1;
    private static final int ID_ATTRIBUTE = 2;

    private final GL_VertexArray vao;
    private final GL_CommandBuffer cbo;
    private final GL_TextureArray font_texture;
    private final GL_Shader shader;

    private final FloatBuffer xy_buffer;
    private final FloatBuffer uv_buffer;
    private final FloatBuffer id_buffer;

    private final Map<Character, TextGlyph> character_map = new HashMap<>();
    private final Queue<Event> event_queue = new LinkedBlockingQueue<>();
    private final Map<String, TextContainer> text_boxes = new HashMap<>();

    private float max_char_height = 0;

    private boolean dirty = true;
    private int current_glyph_count = 0;

    private final Window window;

    public DebugHUDRenderer(ECS<Component> ecs)
    {
        this.window = Component.MainWindow.global(ecs);

        var event_bus = Component.Events.<EventBus>global(ecs);
        event_bus.register(event_queue,
            CoreEvent.WINDOW_RESIZE,
            CoreEvent.FPS,
            DebugEvent.VIEW_PITCH,
            DebugEvent.VIEW_YAW,
            DebugEvent.VIEW_DIST,
            DebugEvent.CAMERA_POSITION,
            DebugEvent.PLAYER_POSITION);

        build_cmd();

        text_boxes.put("DEBUG", new TextContainer(SnapPosition.TOP_LEFT,
            "DEBUG:", 100, 100, .75f));

        text_boxes.put("pitch_label", new TextContainer(SnapPosition.TOP_LEFT,
            "- pitch: ", 100, 150, .75f));

        text_boxes.put("yaw_label", new TextContainer(SnapPosition.TOP_LEFT,
            "- yaw:  ", 100, 200, .75f));

        text_boxes.put("dist_label", new TextContainer(SnapPosition.TOP_LEFT,
            "- dist:  ", 100, 250, .75f));

        text_boxes.put("1_position_label", new TextContainer(SnapPosition.TOP_LEFT,
                "- pos 1:  ", 100, 300, .75f));

        text_boxes.put("2_position_label", new TextContainer(SnapPosition.TOP_LEFT,
                "- pos 2:  ", 100, 350, .75f));

        text_boxes.put("pitch", new TextContainer(SnapPosition.TOP_LEFT,
            "0", 350, 150, .75f));

        text_boxes.put("yaw", new TextContainer(SnapPosition.TOP_LEFT,
            "0", 350, 200, .75f));

        text_boxes.put("dist", new TextContainer(SnapPosition.TOP_LEFT,
            "0", 350, 250, .75f));

        text_boxes.put("1_position", new TextContainer(SnapPosition.TOP_LEFT,
                "0", 350, 300, .75f));

        text_boxes.put("2_position", new TextContainer(SnapPosition.TOP_LEFT,
                "0", 350, 350, .75f));

        text_boxes.put("title", new TextContainer(SnapPosition.BOTTOM_LEFT,
            "The Return of Trongle - Prototype", 100, 100, .75f));

        text_boxes.put("fps_label", new TextContainer(SnapPosition.BOTTOM_RIGHT,
            "FPS", 100, 100, .75f));

        text_boxes.put("fps", new TextContainer(SnapPosition.BOTTOM_RIGHT,
            "-", 100, 150, .75f));

        shader = GPU.GL.new_shader(resources, "text");

        vao = GPU.GL.new_vao(resources);

        var xy_vbo = GPU.GL.vec2_buffer(resources, vao, XY_ATTRIBUTE, VECTOR_FLOAT_2D_SIZE * VERTICES_PER_LETTER * RENDER_BATCH_SIZE);
        var uv_vbo = GPU.GL.vec2_buffer(resources, vao, UV_ATTRIBUTE, VECTOR_FLOAT_2D_SIZE * VERTICES_PER_LETTER * RENDER_BATCH_SIZE);
        var id_vbo = GPU.GL.float_buffer(resources, vao, ID_ATTRIBUTE, SCALAR_FLOAT_SIZE * RENDER_BATCH_SIZE);

        xy_buffer = xy_vbo.map_as_float_buffer_persistent();
        uv_buffer = uv_vbo.map_as_float_buffer_persistent();
        id_buffer = id_vbo.map_as_float_buffer_persistent();

        vao.enable_attribute(XY_ATTRIBUTE);
        vao.enable_attribute(UV_ATTRIBUTE);
        vao.enable_attribute(ID_ATTRIBUTE);
        vao.instance_attribute(ID_ATTRIBUTE, 1);

        cbo = GPU.GL.command_buffer(resources, COMMAND_BUFFER_SIZE);
        cbo.load_int_sub_data(raw_cmd, 0);
        font_texture = GPU.GL.build_character_map(resources, TEXTURE_SIZE, "/font/Inconsolata-Light.ttf", character_map);

        shader.use();
        shader.uploadInt("uTexture", 0);
        shader.detach();

        gather_text_metrics();
    }

    private void build_cmd()
    {
        int cmd_offset = 0;
        for (int i = 0; i < RENDER_BATCH_SIZE; i++)
        {
            int index = i * VERTICES_PER_LETTER;
            raw_cmd[cmd_offset++] = VERTICES_PER_LETTER;
            raw_cmd[cmd_offset++] = 1;
            raw_cmd[cmd_offset++] = index;
            raw_cmd[cmd_offset++] = i;
        }
    }

    private void gather_text_metrics()
    {
        for (var character : character_map.values())
        {
            max_char_height = Math.max(max_char_height, character.size()[1]);
        }
    }

    private float calculate_text_width(String text, float scale)
    {
        float width = 0.0f;
        for (var character : text.toCharArray())
        {
            var glyph = character_map.get(character);
            width += (glyph.advance() >> 6) * scale;
        }
        return width;
    }

    private void rebuild_ui()
    {
        current_glyph_count = 0;
        int pos_offset = 0;
        int uv_offset = 0;
        int id_offset = 0;

        xy_buffer.clear();
        uv_buffer.clear();
        id_buffer.clear();

        var text_containers = new ArrayList<>(text_boxes.values());

        for (var text_box : text_containers)
        {
            float x      = text_box.x();
            float y      = text_box.y();
            float scale  = text_box.scale();

            float window_width  = window.width();
            float window_height = window.height();

            float width = calculate_text_width(text_box.message(), scale);
            float height = max_char_height * scale;

            switch (text_box.snap())
            {
                case NONE, BOTTOM, LEFT, BOTTOM_LEFT -> { }
                case TOP, TOP_LEFT -> y = window_height - height - y;
                case RIGHT, BOTTOM_RIGHT -> x = window_width - width - x;
                case TOP_RIGHT ->
                {
                    y = window_height - height - y;
                    x = window_width - width - x;
                }
            }

            for (var character : text_box.message().toCharArray())
            {
                var glyph = character_map.get(character);
                current_glyph_count++;

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

                xy_buffer.put(pos_offset++, x2);
                xy_buffer.put(pos_offset++, y1);
                xy_buffer.put(pos_offset++, x2);
                xy_buffer.put(pos_offset++, y2);
                xy_buffer.put(pos_offset++, x1);
                xy_buffer.put(pos_offset++, y1);
                xy_buffer.put(pos_offset++, x1);
                xy_buffer.put(pos_offset++, y2);

                uv_buffer.put(uv_offset++, u2);
                uv_buffer.put(uv_offset++, v1);
                uv_buffer.put(uv_offset++, u2);
                uv_buffer.put(uv_offset++, v2);
                uv_buffer.put(uv_offset++, u1);
                uv_buffer.put(uv_offset++, v1);
                uv_buffer.put(uv_offset++, u1);
                uv_buffer.put(uv_offset++, v2);

                id_buffer.put(id_offset++, glyph.texture_id());

                x += (glyph.advance() >> 6) * scale;
            }
        }

        dirty = false;
    }

    @Override
    public void render()
    {
        Event next_event;
        while ((next_event = event_queue.poll()) != null)
        {
            if (next_event.type() == CoreEvent.WINDOW_RESIZE)
            {
                dirty = true;
            }
            if (next_event instanceof PositionEvent(var type, var position))
            {
                if (type == DebugEvent.CAMERA_POSITION) {
                    var current = text_boxes.get("1_position");
                    var next = new TextContainer(current.snap(), position, current.x(), current.y(), current.scale());
                    text_boxes.put("1_position", next);
                }
                if (type == DebugEvent.PLAYER_POSITION)
                {
                    var current = text_boxes.get("2_position");
                    var next = new TextContainer(current.snap(), position, current.x(), current.y(), current.scale());
                    text_boxes.put("2_position", next);
                }
                dirty = true;
            }
            if (next_event instanceof MessageEvent(var type, var message))
            {
                if (Objects.requireNonNull(type) == CoreEvent.FPS)
                {
                    var current = text_boxes.get("fps");
                    var next = new TextContainer(current.snap(), message, current.x(), current.y(), current.scale());
                    text_boxes.put("fps", next);
                    dirty = true;
                }
            }
            if (next_event instanceof ViewDebugEvent(var type, float value))
            {
                switch (type)
                {
                    case VIEW_PITCH ->
                    {
                        var current_pitch = text_boxes.get("pitch");
                        var next_pitch = new TextContainer(current_pitch.snap(), String.valueOf(Math.toDegrees(value)), current_pitch.x(), current_pitch.y(), current_pitch.scale());
                        text_boxes.put("pitch", next_pitch);
                    }

                    case VIEW_YAW ->
                    {
                        var current_yaw = text_boxes.get("yaw");
                        var next_yaw = new TextContainer(current_yaw.snap(), String.valueOf(Math.toDegrees(value)), current_yaw.x(), current_yaw.y(), current_yaw.scale());
                        text_boxes.put("yaw", next_yaw);
                    }

                    case VIEW_DIST ->
                    {
                        var current_dist = text_boxes.get("dist");
                        var next_dist = new TextContainer(current_dist.snap(), String.valueOf(value), current_dist.x(), current_dist.y(), current_dist.scale());
                        text_boxes.put("dist", next_dist);
                    }
                }

                dirty = true;
            }
        }

        if (dirty) rebuild_ui();

        glDisable(GL_DEPTH_TEST);
        vao.bind();
        shader.use();
        font_texture.bind(0);
        cbo.bind();
        glMultiDrawArraysIndirect(GL_TRIANGLE_STRIP, 0, current_glyph_count, 0);
        vao.unbind();
        shader.detach();
        glEnable(GL_DEPTH_TEST);
    }
}
