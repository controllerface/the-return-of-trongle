package com.controllerface.trongle.systems.rendering;

import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.ecs.ECSSystem;
import com.controllerface.trongle.components.Component;
import com.juncture.alloy.ecs.ECSWorld;
import com.juncture.alloy.rendering.RenderComponent;
import org.joml.Vector3f;

public class ParticleSystem extends ECSSystem
{
    private final ECSLayer<Component> ecs;
    private final ECSLayer<RenderComponent> recs;

    public ParticleSystem(ECSWorld world)
    {
        super(world);
        ecs = world.get(Component.class);
        recs = world.get(RenderComponent.class);
    }

    private final Vector3f vel_buffer = new Vector3f();

    @Override
    public void tick(double dt)
    {
        var particles = ecs.get_components(Component.Particle);
        for (var entry : particles.entrySet())
        {
            var projectile_entity = entry.getKey();
            var gravity  = Component.ParticleGravity.<Vector3f>for_entity(ecs, projectile_entity);
            var velocity = Component.ParticleVelocity.<Vector3f>for_entity(ecs, projectile_entity);
            var position = RenderComponent.RenderPosition.<Vector3f>for_entity(recs, projectile_entity);

            velocity.fma((float) dt, gravity);

            vel_buffer.set(velocity).mul((float) dt);
            position.add(vel_buffer);
        }
    }
}
