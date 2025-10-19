package com.controllerface.trongle.systems.rendering;

import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.Renderer;
import com.controllerface.trongle.components.Component;
import com.controllerface.trongle.systems.rendering.passes.debug.BoundingBoxRenderPass;
import com.controllerface.trongle.systems.rendering.passes.debug.ConvexHullRenderPass;
import com.controllerface.trongle.systems.rendering.passes.debug.RayCastRenderPass;
import com.controllerface.trongle.systems.rendering.passes.debug.RenderingBoundsRenderPass;

public class DebugRenderer extends Renderer<Component>
{
    private final boolean DEBUG_HULLS = false;
    private final boolean DEBUG_RAYS = false;
    private final boolean DEBUG_PHYS_BOUNDS = false;
    private final boolean DEBUG_REND_BOUNDS = false;

    private final RenderPass hull_pass;
    private final RenderPass ray_pass;
    private final RenderPass aabb_pass;
    private final RenderPass render_aabb_pass;
    private final RenderPass hud_pass;

    public DebugRenderer(ECS<Component> ecs)
    {
        super(ecs);
        hull_pass = resources.track(new ConvexHullRenderPass(ecs));
        ray_pass = resources.track(new RayCastRenderPass(ecs));
        aabb_pass = resources.track(new BoundingBoxRenderPass(ecs));
        render_aabb_pass = resources.track(new RenderingBoundsRenderPass(ecs));
        hud_pass = resources.track(new DebugHUDRenderer(ecs));
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
