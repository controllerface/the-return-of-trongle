package com.controllerface.trongle.systems.rendering;

import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.ecs.ECSSystem;
import com.juncture.alloy.ecs.ECSWorld;
import com.juncture.alloy.gpu.Renderer;
import com.controllerface.trongle.components.Component;
import com.juncture.alloy.physics.PhysicsComponent;
import com.juncture.alloy.rendering.RenderComponent;

import java.util.ArrayList;
import java.util.List;

public class DebugRenderingSystem extends ECSSystem
{
    private final List<Renderer> renderers = new ArrayList<>();

    private final ECSLayer<Component> ecs;
    private final ECSLayer<PhysicsComponent> pecs;
    private final ECSLayer<RenderComponent> recs;

    public DebugRenderingSystem(ECSWorld world)
    {
        super(world);
        ecs = world.get(Component.class);
        pecs = world.get(PhysicsComponent.class);
        recs = world.get(RenderComponent.class);
        renderers.add(new DebugRenderer(ecs, pecs, recs));
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
