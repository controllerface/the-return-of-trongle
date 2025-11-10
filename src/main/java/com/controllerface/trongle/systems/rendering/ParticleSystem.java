package com.controllerface.trongle.systems.rendering;

import com.controllerface.trongle.systems.physics.PhysicsSystem;
import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.ecs.ECSSystem;
import com.controllerface.trongle.components.Component;
import org.joml.Vector3f;

public class ParticleSystem extends ECSSystem<Component>
{
    public ParticleSystem(ECSLayer<Component> ecs)
    {
        super(ecs);
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
            var position = Component.RenderPosition.<Vector3f>for_entity(ecs, projectile_entity);

            velocity.fma((float) dt, gravity);

            vel_buffer.set(velocity).mul((float) dt);
            position.add(vel_buffer);
        }
    }
}
