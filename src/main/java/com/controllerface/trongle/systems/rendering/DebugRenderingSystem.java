package com.controllerface.trongle.systems.rendering;

import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.ecs.ECSSystem;
import com.juncture.alloy.gpu.Renderer;
import com.controllerface.trongle.components.Component;

import java.util.ArrayList;
import java.util.List;

public class DebugRenderingSystem extends ECSSystem<Component>
{
    private final List<Renderer<Component>> renderers = new ArrayList<>();

    public DebugRenderingSystem(ECS<Component> _ecs)
    {
        super(_ecs);
        renderers.add(new DebugRenderer(ecs));
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
