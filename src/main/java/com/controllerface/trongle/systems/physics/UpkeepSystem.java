package com.controllerface.trongle.systems.physics;

import com.juncture.alloy.data.LightIntensity;
import com.juncture.alloy.data.MutableBoolean;
import com.juncture.alloy.data.MutableDouble;
import com.juncture.alloy.data.MutableFloat;
import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.ecs.ECSSystem;
import com.juncture.alloy.ecs.ECSWorld;
import com.juncture.alloy.events.EventBus;
import com.juncture.alloy.physics.PhysicsComponent;
import com.juncture.alloy.rendering.RenderComponent;
import com.juncture.alloy.rendering.RenderTypes;
import com.juncture.alloy.utils.math.MathEX;
import com.juncture.alloy.utils.math.Quad3d;
import com.juncture.alloy.utils.math.Ray3d;
import com.controllerface.trongle.components.*;
import com.controllerface.trongle.events.EntityDestoryEvent;
import org.joml.Math;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UpkeepSystem extends ECSSystem
{
    private final List<Vector3fc> BLAST_PARTICLE_VECTORS = new ArrayList<>();

    private final Set<String> to_remove = new HashSet<>();
    private final Vector3f particle_buffer = new Vector3f();
    private final Vector3f BLAST_GRAVITY = new Vector3f(0, -120f, 0);
    private final Vector3f DEBRIS_GRAVITY = new Vector3f(0, -1f, 0);

    private final Vector3f DEBRIS_COLOR = new Vector3f(1.0f);
    private final Vector3f EXPLOSION_COLOR = new Vector3f(1.0f, 0.4f, 0.0f);

    private static final double MAX_DEBRIS_LIFETIME = 0.2;
    private static final double MAX_EXPLOSION_LIFETIME = 1.0;
    private static final double MAX_EXPLOSION_DEBRIS_LIFETIME = 5.0;
    private static final float EXPLOSION_SPEED = 50.0f;
    private static final float DEBRIS_SPEED = 100;
    private static final float DEBRIS_SIZE = 1.25f;
    private static final float EXPLOSION_DEBRIS_SIZE = 1.35f;
    private static final float EXPLOSION_SIZE = 50f;
    private static final float DEBRIS_SPREAD_SIZE = 2f;
    private static final float DEBRIS_SPREAD_ACCURACY = .1f;
    private static final float EXPLOSION_LIGHT_INTENSITY_MAX = 500.0f;
    private static final float EXPLOSION_LIGHT_INTENSITY_MIN = -250.0f;
    private static final float EXPLOSION_LIGHT_RANGE = 50.0f;

    private final EventBus event_bus;

    private final ECSLayer<Component> ecs;
    private final ECSLayer<PhysicsComponent> pecs;
    private final ECSLayer<RenderComponent> recs;

    public UpkeepSystem(ECSWorld world)
    {
        super(world);

        this.ecs  = this.world.get(Component.class);
        this.pecs = this.world.get(PhysicsComponent.class);
        this.recs = this.world.get(RenderComponent.class);

        MathEX.generate_octal_spread(BLAST_PARTICLE_VECTORS);
        event_bus = Component.Events.global(ecs);
    }

    private void spawn_debris_particle(Vector3f particle_velocity,
                                       Vector3f blast_location,
                                       Vector3f color,
                                       Vector3f gravity,
                                       float speed,
                                       float size,
                                       double lifetime)
    {
        MathEX.perturb_direction_float(particle_velocity, DEBRIS_SPREAD_ACCURACY, DEBRIS_SPREAD_SIZE);
        particle_velocity.mul(speed);
        var entity = world.new_entity();
        RenderTypes.billboard(recs, entity, blast_location, size);
        Archetypes.particle(ecs, entity, particle_velocity, gravity, color, lifetime);
    }

    private void spawn_blast(Vector3f blast_location)
    {
        var blast_entity = world.new_entity();
        RenderTypes.billboard(recs, blast_entity, blast_location, EXPLOSION_SIZE);
        RenderTypes.blast_light(recs, blast_entity, blast_location, EXPLOSION_COLOR, EXPLOSION_LIGHT_INTENSITY_MAX, EXPLOSION_LIGHT_RANGE);
        Archetypes.explosion(ecs, blast_entity, MAX_EXPLOSION_LIFETIME);

        for (var particle_vector : BLAST_PARTICLE_VECTORS)
        {
            var particle_velocity = new Vector3f(particle_vector);
            spawn_debris_particle(particle_velocity,
                blast_location,
                EXPLOSION_COLOR,
                BLAST_GRAVITY,
                EXPLOSION_SPEED,
                EXPLOSION_DEBRIS_SIZE,
                MAX_EXPLOSION_DEBRIS_LIFETIME);
        }
    }

    private void update_projectile(String projectile_entity)
    {
        var hit_found = PhysicsComponent.RayCastFound.<MutableBoolean>for_entity(pecs, projectile_entity);
        if (!hit_found.value) return;

        var hit_entity = PhysicsComponent.HitScanResult.<String>for_entity(pecs, projectile_entity);
        var destructible = Component.Destructible.for_entity_or_null(ecs, hit_entity);
        if (destructible == null) return;

        var integrity = Component.Integrity.<MutableFloat>for_entity(ecs, hit_entity);
        var damage = Component.ProjectileDamage.<MutableFloat>for_entity(ecs, projectile_entity);

        integrity.value -= damage.value;
        if (integrity.value <= 0)
        {
            to_remove.add(hit_entity);
            var blast_location = RenderComponent.RenderPosition.<Vector3f>for_entity(recs, hit_entity);
            spawn_blast(blast_location);
        }

        // unset the found flag to avoid projectile being counted twice
        hit_found.value = false;

        var hit_location = PhysicsComponent.RayCastHit.<Vector3d>for_entity(pecs, projectile_entity);

        var quad = RenderComponent.HitScanTrail.<Quad3d>for_entity(recs, projectile_entity);

        particle_buffer.set(hit_location);
        var ray = PhysicsComponent.RayCast.<Ray3d>for_entity(pecs, projectile_entity);

        MathEX.update_tracer_quad(ray.direction(), hit_location, quad, 0.2f);
        recs.set_component(projectile_entity, RenderComponent.HitScanTrail, quad);

        var debris_velocity = new Vector3f().set(ray.direction()).negate();
        spawn_debris_particle(debris_velocity,
            particle_buffer,
            DEBRIS_COLOR,
            DEBRIS_GRAVITY,
            DEBRIS_SPEED,
            DEBRIS_SIZE,
            MAX_DEBRIS_LIFETIME);
    }

    private void update_explosion(String entity)
    {
        var explosion = Component.Explosion.for_entity_or_null(ecs, entity);
        if (explosion == null) return;

        var light_intensity = RenderComponent.LightIntensity.<LightIntensity>for_entity(recs, entity);
        var lifetime        = Component.Lifetime.<MutableDouble>for_entity(ecs, entity);
        var max_lifetime    = Component.MaxLifetime.<MutableDouble>for_entity(ecs, entity);


        float intensity = (float)(lifetime.value / max_lifetime.value);

        intensity = MathEX.map(intensity, 0, 1, EXPLOSION_LIGHT_INTENSITY_MIN, EXPLOSION_LIGHT_INTENSITY_MAX);
        intensity = Math.max(0, intensity);
        light_intensity.diffuse = intensity;
        light_intensity.ambient = intensity / 10;
    }

    private void update_hitscan_weapon(String entity)
    {
        var hit_scan = Component.HitScanWeapon.for_entity_or_null(ecs, entity);
        if (hit_scan == null) return;

        var life = Component.Lifetime.<MutableDouble>for_entity(ecs, entity);
        var max_life = Component.MaxLifetime.<MutableDouble>for_entity(ecs, entity);
        var quad = RenderComponent.HitScanTrail.<Quad3d>for_entity(recs, entity);
        var quad_r = RenderComponent.HitScanTrailRender.<Quad3d>for_entity(recs, entity);
        var l_pos = RenderComponent.RenderPosition.<Vector3f>for_entity(recs, entity);

        float blend = (float)(life.value / max_life.value);
        float tail_animation = 1.0f - blend;
        float tip_animation = java.lang.Math.min(tail_animation * 1.5f, 1.0f);

        quad.tail_right().lerp(quad.tip_right(), tip_animation , quad_r.tip_right());
        quad.tail_left().lerp(quad.tip_left(), tip_animation, quad_r.tip_left());
        quad.tail_right().lerp(quad.tip_right(), tail_animation, quad_r.tail_right());
        quad.tail_left().lerp(quad.tip_left(), tail_animation, quad_r.tail_left());

        l_pos.set(quad_r.tip_right());
    }

    @Override
    public void tick(double dt)
    {
        to_remove.clear();

        var projectiles = ecs.get_components(Component.Projectile);
        for (var entry : projectiles.entrySet())
        {
            var projectile_entity = entry.getKey();
            update_projectile(projectile_entity);
            update_hitscan_weapon(projectile_entity);
        }

        var lifetimes = ecs.get_components(Component.Lifetime);
        for (var entry : lifetimes.entrySet())
        {
            var entity = entry.getKey();
            MutableDouble lifetime = Component.Lifetime.coerce(entry.getValue());
            lifetime.value -= dt;
            if (lifetime.value <= 0) to_remove.add(entity);
            else update_explosion(entity);
        }

        to_remove.stream()
            .map(EntityDestoryEvent::destroy)
            .forEach(event_bus::emit_event);

        ecs.remove_entities(to_remove);

    }
}
