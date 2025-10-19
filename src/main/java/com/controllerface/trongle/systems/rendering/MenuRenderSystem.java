package com.controllerface.trongle.systems.rendering;

import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.ecs.ECSSystem;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.GPUResourceGroup;
import com.juncture.alloy.gpu.Renderer;
import com.juncture.alloy.gpu.Window;
import com.juncture.alloy.gpu.gl.textures.GL_Texture;
import com.controllerface.trongle.components.Component;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL42C.*;

public class MenuRenderSystem extends ECSSystem<Component>
{
    private final List<Renderer<Component>> renderers = new ArrayList<>();
    private final float r, g, b, a;

    private final GPUResourceGroup resources = new GPUResourceGroup();
    private final GL_Texture gl_texture;

    private final Window window;

    public MenuRenderSystem(ECS<Component> _ecs)
    {
        super(_ecs);

        window = Component.MainWindow.global(_ecs);

        gl_texture = GPU.GL.new_texture(resources, true, "/img/bg.png");

        renderers.add(new MenuBGRenderer(ecs, gl_texture));
//        renderers.add(new MenuRenderer_UI(ecs));
        renderers.add(new MenuRenderer(ecs));

        this.r = 0.01f;
        this.g = 0.01f;
        this.b = 0.01f;
        this.a = 1;
    }

    public void capture_screen()
    {
        gl_texture.capture_framebuffer(window.width(), window.height());
    }

    @Override
    public void tick(double dt)
    {
        glClearColor(r, g, b, a);
        glClearDepth(1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        for (var renderer : renderers)
        {
            renderer.render();
        }
    }

    @Override
    public void shutdown()
    {
        resources.release_all();
        for (var renderer : renderers)
        {
            renderer.destroy();
        }
    }
}
