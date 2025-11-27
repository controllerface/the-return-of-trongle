package com.controllerface.trongle.components;

import com.controllerface.trongle.systems.behavior.EntityBehavior;
import com.juncture.alloy.data.*;
import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.rendering.RenderComponent;

public class Archetypes
{
    public static void player(ECSLayer<Component> ecs, ECSLayer<RenderComponent> recs, String entity)
    {
        recs.set_component(entity, RenderComponent.CameraFollow, Marker.MARKED);
        ecs.set_component(entity, Component.Player,   Marker.MARKED);
        ecs.set_component(entity, Component.Behavior, EntityBehavior.PLAYER);
    }

    public static void explosion(ECSLayer<Component> ecs, ECSLayer<RenderComponent> recs, String entity, double lifetime)
    {
        ecs.set_component(entity, Component.Explosion, Marker.MARKED);
        recs.set_component(entity, RenderComponent.Lifetime, new MutableDouble(lifetime));
        recs.set_component(entity, RenderComponent.MaxLifetime, new MutableDouble(lifetime));
    }
}
