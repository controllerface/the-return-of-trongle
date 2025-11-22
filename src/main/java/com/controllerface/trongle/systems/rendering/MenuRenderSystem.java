package com.controllerface.trongle.systems.rendering;

import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.ecs.ECSSystem;
import com.juncture.alloy.ecs.ECSWorld;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.GPUResourceGroup;
import com.juncture.alloy.gpu.Renderer;
import com.juncture.alloy.gpu.Window;
import com.juncture.alloy.gpu.gl.textures.GL_Texture;
import com.controllerface.trongle.components.Component;
import com.juncture.alloy.rendering.RenderComponent;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL42C.*;

public class MenuRenderSystem extends ECSSystem
{
    private final List<Renderer> renderers = new ArrayList<>();
    private final float r, g, b, a;

    private final GPUResourceGroup resources = new GPUResourceGroup();
    private final GL_Texture gl_texture;

    private final Window window;

    private final ECSLayer<Component> ecs;
    private final ECSLayer<RenderComponent> recs;

    public MenuRenderSystem(ECSWorld world)
    {
        super(world);

        this.ecs = world.get(Component.class);
        this.recs = world.get(RenderComponent.class);

        window = RenderComponent.MainWindow.global(recs);

        gl_texture = GPU.GL.new_texture(resources, true, "/img/bg.png");

        renderers.add(new MenuBGRenderer(gl_texture));
//        renderers.add(new MenuRenderer_UI(ecs));
        renderers.add(new MenuRenderer(ecs, recs));

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
