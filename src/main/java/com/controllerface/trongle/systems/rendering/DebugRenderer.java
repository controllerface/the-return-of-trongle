package com.controllerface.trongle.systems.rendering;

import com.juncture.alloy.ecs.ECSWorld;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.Renderer;
import com.controllerface.trongle.components.Component;
import com.controllerface.trongle.systems.rendering.passes.debug.BoundingBoxRenderPass;
import com.controllerface.trongle.systems.rendering.passes.debug.ConvexHullRenderPass;
import com.controllerface.trongle.systems.rendering.passes.debug.RayCastRenderPass;
import com.controllerface.trongle.systems.rendering.passes.debug.RenderingBoundsRenderPass;
import com.juncture.alloy.physics.PhysicsComponent;
import com.juncture.alloy.rendering.RenderComponent;

public class DebugRenderer extends Renderer
{
    private final boolean DEBUG_HULLS = true;
    private final boolean DEBUG_RAYS = true;
    private final boolean DEBUG_PHYS_BOUNDS = true;
    private final boolean DEBUG_REND_BOUNDS = true;

    private final RenderPass hull_pass;
    private final RenderPass ray_pass;
    private final RenderPass aabb_pass;
    private final RenderPass render_aabb_pass;
    private final RenderPass hud_pass;

    public DebugRenderer(ECSWorld world)
    {
        var ecs = world.get(Component.class);
        var pecs = world.get(PhysicsComponent.class);
        var recs = world.get(RenderComponent.class);

        hull_pass = resources.track(new ConvexHullRenderPass(pecs));
        ray_pass = resources.track(new RayCastRenderPass(pecs));
        aabb_pass = resources.track(new BoundingBoxRenderPass(ecs, pecs, recs));
        render_aabb_pass = resources.track(new RenderingBoundsRenderPass(ecs, recs));
        hud_pass = resources.track(new DebugHUDRenderer(world));
    }

    @Override
    public void render()
    {
        if (DEBUG_HULLS) hull_pass.render();
        if (DEBUG_RAYS) ray_pass.render();
        if (DEBUG_PHYS_BOUNDS) aabb_pass.render();
        if (DEBUG_REND_BOUNDS) render_aabb_pass.render();
        hud_pass.render();
    }
}
