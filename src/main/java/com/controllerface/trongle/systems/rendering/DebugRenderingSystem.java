package com.controllerface.trongle.systems.rendering;

import com.juncture.alloy.ecs.ECSSystem;
import com.juncture.alloy.ecs.ECSWorld;
import com.juncture.alloy.gpu.Renderer;

import java.util.ArrayList;
import java.util.List;

public class DebugRenderingSystem extends ECSSystem
{
    private final List<Renderer> renderers = new ArrayList<>();

    public DebugRenderingSystem(ECSWorld world)
    {
        super(world);
        renderers.add(new DebugRenderer(world));
    }

    @Override
    public void tick(double dt)
    {
        for (var renderer : renderers)
        {
            renderer.render();
        }
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
