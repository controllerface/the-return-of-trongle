package com.controllerface.trongle.systems.rendering.passes.debug;

import com.juncture.alloy.camera.WorldCamera;
import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.Window;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexArray;
import com.juncture.alloy.gpu.gl.shaders.GL_Shader;
import com.juncture.alloy.gpu.gl.textures.GL_ShadowTexture;
import com.controllerface.trongle.components.Component;
import org.joml.Matrix4f;

import static com.controllerface.trongle.systems.rendering.GeometryRenderer.Texture_BindPoint.SHADOW_MAP;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.glDrawArrays;

public class ShadowDebugPass extends RenderPass
{
    private static final int POS_ATTRIBUTE = 0;
    private static final int UV_ATTRIBUTE = 1;

    private final WorldCamera camera;
    private final Window window;

    private final GL_VertexArray vao;
    private final GL_Shader shader;
    private final GL_ShadowTexture shadow_texture;

    private final Matrix4f light_space_matrix;
    private final Matrix4f invView = new Matrix4f();
    private final Matrix4f invProj = new Matrix4f();

    float[] quadPositions = {
        -1.0f,  1.0f,
        -1.0f, -1.0f,
        1.0f, -1.0f,

        -1.0f,  1.0f,
        1.0f, -1.0f,
        1.0f,  1.0f
    };

    float[] quadTexCoords = {
        0.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 0.0f,

        0.0f, 1.0f,
        1.0f, 0.0f,
        1.0f, 1.0f
    };

    public ShadowDebugPass(ECS<Component> ecs, GL_ShadowTexture shadow_texture)
    {
        this.shadow_texture = shadow_texture;
        this.light_space_matrix = Component.LightSpaceMatrix.global(ecs);
        this.camera = Component.MainCamera.global(ecs);
        this.window = Component.MainWindow.global(ecs);

        vao     = GPU.GL.new_vao(resources);
        shader  = GPU.GL.new_shader(resources,"shadow_debug");

        GPU.GL.vec2_buffer_static(resources, vao, POS_ATTRIBUTE, quadPositions);
        GPU.GL.vec2_buffer_static(resources, vao, UV_ATTRIBUTE, quadTexCoords);

        vao.enable_attribute(POS_ATTRIBUTE);
        vao.enable_attribute(UV_ATTRIBUTE);

        shader.use();
        shader.uploadInt(SHADOW_MAP.varName, 0);
        shader.detach();
    }

    @Override
    public void render()
    {

        float screenAspect = (float) window.width() / window.width();
        float shadowAspect = 1.0f; // Shadow map is square (2048 x 2048)

        float scaleX = 1.0f;
        float scaleY = 1.0f;

        if (screenAspect > shadowAspect) {
            scaleX = shadowAspect / screenAspect;
        } else {
            scaleY = screenAspect / shadowAspect;
        }



//        camera.view_matrix().invert(invView);
//        camera.projection_matrix().invert(invProj);
        vao.bind();
        shader.use();
        shadow_texture.bind(0);
        shader.uploadMat4f("lightSpaceMatrix", light_space_matrix);
        //shader.uploadvec2f("shadowMapScale", new Vector2f(scaleX, scaleY));
//        shader.uploadMat4f("invProjection", invProj);
//        shader.uploadMat4f("invView", invView);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        shader.detach();
        vao.unbind();
    }
}
