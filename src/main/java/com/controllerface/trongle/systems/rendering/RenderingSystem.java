package com.controllerface.trongle.systems.rendering;

import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.ecs.ECSSystem;
import com.juncture.alloy.gpu.Renderer;
import com.juncture.alloy.gpu.gl.GL_GraphicsController;
import com.controllerface.trongle.components.Component;

import java.util.ArrayList;
import java.util.List;

import static com.juncture.alloy.gpu.gl.GL_GraphicsController.BufferType.COLOR;
import static com.juncture.alloy.gpu.gl.GL_GraphicsController.BufferType.DEPTH;

public class RenderingSystem extends ECSSystem<Component>
{
    private final float r, g, b, a;
    private final float z;
    private final List<Renderer<Component>> renderers = new ArrayList<>();
    private final GL_GraphicsController gl_controller;

    public RenderingSystem(ECSLayer<Component> _ecs, GL_GraphicsController gl_controller)
    {
        super(_ecs);

        this.gl_controller = gl_controller;
        this.gl_controller.init_sync();

        this.r = 0.01f;
        this.g = 0.01f;
        this.b = 0.01f;
        this.a = 1.0f;
        this.z = 1.0f;

        renderers.add(new GeometryRenderer(ecs));
        //renderers.add(new HUDRenderer(ecs));
    }

    @Override
    public void tick(double dt)
    {
        // todo: eventually, sRGB toggle will only be for final pass, and will be disabled for intermediate passes
        gl_controller.toggle_sRGB(true);
        gl_controller.wait_sync();
        gl_controller.clear_color(r, g, b, a);
        gl_controller.clear_depth(z);
        gl_controller.clear_buffer_bits(COLOR, DEPTH);

        for (var renderer : renderers)
        {
            renderer.render();
        }

        gl_controller.toggle_sRGB(false);
        gl_controller.reset_sync();
    }

    @Override
    public void shutdown()
    {
        for (var renderer : renderers)
        {
            renderer.destroy();
        }
    }
}
