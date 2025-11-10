package com.controllerface.trongle.systems.rendering;

import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.Renderer;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexArray;
import com.juncture.alloy.gpu.gl.shaders.GL_Shader;
import com.juncture.alloy.gpu.gl.textures.GL_Texture;
import com.controllerface.trongle.components.Component;

import static com.controllerface.trongle.systems.rendering.GeometryRenderer.Texture_BindPoint.BACKGROUND;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL11C.glDrawArrays;

public class MenuBGRenderer extends Renderer<Component>
{
    private static final int XY_ATTRIBUTE = 0;
    private static final int UV_ATTRIBUTE = 1;

    private final GL_VertexArray vao;
    private final GL_Shader shader;
    private final GL_Texture bg_texture;

    public static float[] SCREEN_QUAD_VERTICES = new float[]
        {
            -1.0f, -1.0f, // bottom-left
             1.0f, -1.0f, // bottom-right
            -1.0f,  1.0f, // top-left
             1.0f,  1.0f, // top-right
        };

    public static float[] UVS =
        {
            0.0f, 0.0f, // bottom-left
            1.0f, 0.0f, // bottom-right
            0.0f, 1.0f, // top-left
            1.0f, 1.0f, // top-right
        };

    public MenuBGRenderer(ECSLayer<Component> _ecs, GL_Texture bg_texture)
    {
        super(_ecs);
        this.bg_texture = bg_texture;

        vao     = GPU.GL.new_vao(resources);
        shader  = GPU.GL.new_shader(resources, "background");

        GPU.GL.vec2_buffer_static(resources, vao, XY_ATTRIBUTE, SCREEN_QUAD_VERTICES);
        GPU.GL.vec2_buffer_static(resources, vao, UV_ATTRIBUTE, UVS);

        vao.enable_attribute(XY_ATTRIBUTE);
        vao.enable_attribute(UV_ATTRIBUTE);

        shader.use();
        shader.uploadInt(BACKGROUND.varName, 0);
        shader.detach();
    }

    @Override
    public void render()
    {
        vao.bind();
        shader.use();
        bg_texture.bind(0);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        shader.detach();
        vao.unbind();
    }
}
