package com.controllerface.trongle.components;

import com.controllerface.trongle.systems.behavior.EntityBehavior;
import com.juncture.alloy.data.*;
import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.utils.math.*;
import org.joml.Vector3f;

public class Archetypes
{
    public static void player(ECSLayer<Component> ecs, String entity)
    {
        ecs.set_component(entity, Component.Player,   Marker.MARKED);
        ecs.set_component(entity, Component.Behavior, EntityBehavior.PLAYER);
    }

    public static void destructible(ECSLayer<Component> ecs, String entity, float initial_integrity)
    {
        ecs.set_component(entity, Component.Destructible, Marker.MARKED);
        ecs.set_component(entity, Component.Integrity, new MutableFloat(initial_integrity));
    }

    public static void particle(ECSLayer<Component> ecs,
                                String entity,
                                Vector3f velocity,
                                Vector3f gravity,
                                Vector3f color,
                                double lifetime)
    {
        ecs.set_component(entity, Component.ParticleVelocity, velocity);
        ecs.set_component(entity, Component.ParticleColor, color);
        ecs.set_component(entity, Component.ParticleGravity, gravity);
        ecs.set_component(entity, Component.Particle, Marker.MARKED);
        ecs.set_component(entity, Component.Lifetime, new MutableDouble(lifetime));
        ecs.set_component(entity, Component.MaxLifetime, new MutableDouble(lifetime));
    }

    public static void explosion(ECSLayer<Component> ecs,
                                 String entity,
                                 double lifetime)
    {
        ecs.set_component(entity, Component.Explosion, Marker.MARKED);
        ecs.set_component(entity, Component.Lifetime, new MutableDouble(lifetime));
        ecs.set_component(entity, Component.MaxLifetime, new MutableDouble(lifetime));
    }
}
