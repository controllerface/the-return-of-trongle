package com.controllerface.trongle.systems.rendering.passes.base;

import com.juncture.alloy.data.MutableFloat;
import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexArray;
import com.juncture.alloy.gpu.gl.shaders.GL_Shader;
import com.juncture.alloy.gpu.gl.textures.GL_ShadowTexture;
import com.controllerface.trongle.components.Component;
import org.joml.Matrix4f;

import static com.controllerface.trongle.systems.rendering.GeometryRenderer.Texture_BindPoint.SHADOW_MAP;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL11C.glDrawArrays;

public class CloudRenderPass extends RenderPass
{
    private static final float CLOUD_HEIGHT = -200.0f;
    private static final float WORLD_DIMENSION = 32_768.0f;
    private static final int XYZ_ATTRIBUTE = 0;
    private static final int UV_ATTRIBUTE = 1;

    private static final float[] VERTICES =
        {
             WORLD_DIMENSION, CLOUD_HEIGHT, -WORLD_DIMENSION,
            -WORLD_DIMENSION, CLOUD_HEIGHT, -WORLD_DIMENSION,
             WORLD_DIMENSION, CLOUD_HEIGHT,  WORLD_DIMENSION,
            -WORLD_DIMENSION, CLOUD_HEIGHT,  WORLD_DIMENSION,
        };

    public static final float[] UVS =
        {
            0.0f, 0.0f, // Bottom-left
            0.0f, 1.0f, // Bottom-right
            1.0f, 0.0f, // Top-left
            1.0f, 1.0f, // Top-right
        };

    private final GL_VertexArray vao;
    private final GL_Shader shader;
    private final GL_ShadowTexture shadow_texture;
    private final Matrix4f light_space_matrix;

    private final MutableFloat time_index;

    public CloudRenderPass(ECSLayer<Component> ecs, GL_ShadowTexture shadow_texture)
    {
        this.shadow_texture = shadow_texture;
        this.light_space_matrix = Component.LightSpaceMatrix.global(ecs);

        time_index = Component.TimeIndex.global(ecs);
        vao     = GPU.GL.new_vao(resources);
        shader  = GPU.GL.new_shader(resources, "clouds");

        GPU.GL.vec3_buffer_static(resources, vao, XYZ_ATTRIBUTE, VERTICES);
        GPU.GL.vec2_buffer_static(resources, vao, UV_ATTRIBUTE, UVS);

        vao.enable_attribute(XYZ_ATTRIBUTE);
        vao.enable_attribute(UV_ATTRIBUTE);

        shader.use();
        shader.uploadInt(SHADOW_MAP.varName, SHADOW_MAP.ordinal());
        shader.detach();
    }

    @Override
    public void render()
    {
        vao.bind();
        shader.use();
        shader.uploadFloat("uTime", time_index.value);
        shader.uploadMat4f("lightSpaceMatrix", light_space_matrix);
        shadow_texture.bind(SHADOW_MAP.ordinal());
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        shader.detach();
        vao.unbind();
    }
}
