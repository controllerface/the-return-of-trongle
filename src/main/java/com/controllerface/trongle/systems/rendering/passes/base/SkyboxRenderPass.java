package com.controllerface.trongle.systems.rendering.passes.base;

import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexArray;
import com.juncture.alloy.gpu.gl.shaders.GL_Shader;
import com.juncture.alloy.gpu.gl.textures.GL_CubeMap;

import static com.controllerface.trongle.systems.rendering.GeometryRenderer.Texture_BindPoint.SKYBOX_DAY_TEXTURE;
import static com.controllerface.trongle.systems.rendering.GeometryRenderer.Texture_BindPoint.SKYBOX_NIGHT_TEXTURE;
import static org.lwjgl.opengl.GL11C.*;

public class SkyboxRenderPass extends RenderPass
{
    private static final int XYZ_ATTRIBUTE = 0;

    private static final float[] SKYBOX_VERTICES =
        {
            -1.0f,  1.0f, -1.0f,   -1.0f, -1.0f, -1.0f,    1.0f, -1.0f, -1.0f,    1.0f, -1.0f, -1.0f,    1.0f,  1.0f, -1.0f,   -1.0f,  1.0f, -1.0f,
            -1.0f, -1.0f,  1.0f,   -1.0f, -1.0f, -1.0f,   -1.0f,  1.0f, -1.0f,   -1.0f,  1.0f, -1.0f,   -1.0f,  1.0f,  1.0f,   -1.0f, -1.0f,  1.0f,
             1.0f, -1.0f, -1.0f,    1.0f, -1.0f,  1.0f,    1.0f,  1.0f,  1.0f,    1.0f,  1.0f,  1.0f,    1.0f,  1.0f, -1.0f,    1.0f, -1.0f, -1.0f,
            -1.0f, -1.0f,  1.0f,   -1.0f,  1.0f,  1.0f,    1.0f,  1.0f,  1.0f,    1.0f,  1.0f,  1.0f,    1.0f, -1.0f,  1.0f,   -1.0f, -1.0f,  1.0f,
            -1.0f,  1.0f, -1.0f,    1.0f,  1.0f, -1.0f,    1.0f,  1.0f,  1.0f,    1.0f,  1.0f,  1.0f,   -1.0f,  1.0f,  1.0f,   -1.0f,  1.0f, -1.0f,
            -1.0f, -1.0f, -1.0f,   -1.0f, -1.0f,  1.0f,    1.0f, -1.0f, -1.0f,    1.0f, -1.0f, -1.0f,   -1.0f, -1.0f,  1.0f,    1.0f, -1.0f,  1.0f
        };

    private final GL_VertexArray vao;
    private final GL_Shader shader;
    private final GL_CubeMap day_texture;
    private final GL_CubeMap night_texture;

    public SkyboxRenderPass(GL_CubeMap skybox_texture, GL_CubeMap skybox_texture_dark)
    {
        vao     = GPU.GL.new_vao(resources);
        day_texture = skybox_texture;
        night_texture = skybox_texture_dark;
        shader  = GPU.GL.new_shader(resources,"skybox");

        GPU.GL.vec3_buffer_static(resources, vao, XYZ_ATTRIBUTE, SKYBOX_VERTICES);

        vao.enable_attribute(XYZ_ATTRIBUTE);

        shader.use();
        shader.uploadInt(SKYBOX_DAY_TEXTURE.varName, SKYBOX_DAY_TEXTURE.ordinal());
        shader.uploadInt(SKYBOX_NIGHT_TEXTURE.varName, SKYBOX_NIGHT_TEXTURE.ordinal());
        shader.detach();
    }

    @Override
    public void render()
    {
        glDepthFunc(GL_LEQUAL);
        vao.bind();
        shader.use();
        day_texture.bind(SKYBOX_DAY_TEXTURE.ordinal());
        night_texture.bind(SKYBOX_NIGHT_TEXTURE.ordinal());
        glDrawArrays(GL_TRIANGLES, 0, 36);
        shader.detach();
        vao.unbind();
        glDepthFunc(GL_LESS);
    }
}
