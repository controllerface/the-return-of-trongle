package com.controllerface.trongle.components;

import com.controllerface.trongle.systems.behavior.EntityBehavior;
import com.juncture.alloy.data.*;
import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.models.ModelRegistry;
import com.juncture.alloy.models.data.Light;
import com.juncture.alloy.utils.math.*;
import com.controllerface.trongle.main.GLTFModel;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class Archetypes
{
    public static void player(ECS<Component> ecs, String entity)
    {
        ecs.set_component(entity, Component.Player,   Marker.MARKED);
        ecs.set_component(entity, Component.Behavior, EntityBehavior.PLAYER);
    }

    public static void model(ECS<Component> ecs, String entity, GLTFModel model, float scale)
    {
        var model_lights = init_lights(ecs, model, scale);
        ecs.set_component(entity, Component.Model, model);
        ecs.set_component(entity, Component.ModelLights, model_lights);
        ecs.set_component(entity, Component.RenderBounds, new Bounds3f(entity));
        ecs.set_component(entity, Component.RenderPosition, new Vector3f(0.0f));
        ecs.set_component(entity, Component.RenderScale, new Vector3f(0.0f));
        ecs.set_component(entity, Component.RenderRotation, new Vector3f(0.0f));
        ecs.set_component(entity, Component.Transform, new Matrix4f());
    }

    public static void point_light(ECS<Component> ecs, String entity, Light light, float scale)
    {
        ecs.set_component(entity, Component.Light, LightEmitterType.POINT);
        ecs.set_component(entity, Component.Color, new Vector4f(light.data().color(), 1.0f));
        ecs.set_component(entity, Component.RenderPosition, new Vector3f(light.data().position()));
        ecs.set_component(entity, Component.LightIntensity, new LightIntensity(light.data().intensity() / 10, light.data().intensity()));
        ecs.set_component(entity, Component.LightRange, new MutableFloat(light.data().range() * scale));
    }

    public static void spot_light(ECS<Component> ecs, String entity, Light light, float scale)
    {
        ecs.set_component(entity, Component.Light, LightEmitterType.SPOT);
        ecs.set_component(entity, Component.Color, new Vector4f(light.data().color(), 1.0f));
        ecs.set_component(entity, Component.RenderPosition, new Vector3f().set(light.data().position()));
        ecs.set_component(entity, Component.Direction, new Vector3f(light.data().direction()));
        ecs.set_component(entity, Component.InnerCone, new MutableFloat((float) Math.cos(light.data().inner_cone())));
        ecs.set_component(entity, Component.OuterCone, new MutableFloat((float) Math.cos(light.data().outer_cone())));
        ecs.set_component(entity, Component.LightIntensity, new LightIntensity(light.data().intensity() / 10, light.data().intensity()));
        ecs.set_component(entity, Component.LightRange, new MutableFloat(light.data().range() * scale));
    }

    public static void blast_light(ECS<Component> ecs, String entity, Vector3f origin, Vector3f color, float intensity, float range)
    {
        ecs.set_component(entity, Component.Light, LightEmitterType.POINT);
        ecs.set_component(entity, Component.Color, new Vector4f(color.x, color.y, color.z, 1.0f));
        ecs.set_component(entity, Component.RenderPosition, origin);
        ecs.set_component(entity, Component.LightIntensity, new LightIntensity(intensity / 10, intensity));
        ecs.set_component(entity, Component.LightRange, new MutableFloat(range));
    }

    public static void destructible(ECS<Component> ecs, String entity, float initial_integrity)
    {
        ecs.set_component(entity, Component.Destructible, Marker.MARKED);
        ecs.set_component(entity, Component.Integrity, new MutableFloat(initial_integrity));
    }

    public static void particle(ECS<Component> ecs,
                                String entity,
                                Vector3f origin,
                                Vector3f velocity,
                                Vector3f gravity,
                                Vector3f color,
                                float size,
                                double lifetime)
    {
        ecs.set_component(entity, Component.ParticleVelocity, velocity);
        ecs.set_component(entity, Component.ParticleColor, color);
        ecs.set_component(entity, Component.ParticleGravity, gravity);
        ecs.set_component(entity, Component.Particle, Marker.MARKED);
        ecs.set_component(entity, Component.BillboardSize, new MutableFloat(size));
        ecs.set_component(entity, Component.RenderPosition, new Vector3f().set(origin));
        ecs.set_component(entity, Component.Lifetime, new MutableDouble(lifetime));
        ecs.set_component(entity, Component.MaxLifetime, new MutableDouble(lifetime));
    }

    public static void explosion(ECS<Component> ecs, String entity, Vector3f origin, Vector3f color, float light_intensity, float light_range, float size, double lifetime)
    {
        blast_light(ecs, entity, origin, color, light_intensity, light_range);
        ecs.set_component(entity, Component.Explosion, Marker.MARKED);
        ecs.set_component(entity, Component.BillboardSize, new MutableFloat(size));
        ecs.set_component(entity, Component.RenderPosition, new Vector3f(origin));
        ecs.set_component(entity, Component.Lifetime, new MutableDouble(lifetime));
        ecs.set_component(entity, Component.MaxLifetime, new MutableDouble(lifetime));
    }

    public static void mouse_target(ECS<Component> ecs, String entity, Ray3d ray)
    {
        ray_cast(ecs, entity, ray, false);
        ecs.set_component(entity, Component.MouseCollider, Marker.MARKED);
    }

    public static void ray_cast(ECS<Component> ecs, String entity, Ray3d ray, boolean interact)
    {
        ecs.set_component(entity, Component.RayCast, ray);
        ecs.set_component(entity, Component.RayCastHit, new Vector3d(ray.endpoint()));
        ecs.set_component(entity, Component.RayCastFound, new MutableBoolean(false));
        ecs.set_component(entity, Component.RayCastComplete, new MutableBoolean(false));
        ecs.set_component(entity, Component.RayCastInteract, interact);
    }

    public static void hit_scan_projectile(ECS<Component> ecs,
                                           String entity,
                                           Ray3d ray,
                                           Vector3f tip_color,
                                           Vector3f tail_color,
                                           float light_intensity,
                                           float light_range,
                                           float damage,
                                           double lifetime)
    {
        var trail_quad = MathEX.generate_tracer_quad(ray, 1f);
        ray_cast(ecs, entity, ray, true);
        blast_light(ecs, entity, new Vector3f(ray.origin()), tip_color, light_intensity, light_range);
        ecs.set_component(entity, Component.HitScanWeapon, Marker.MARKED);
        ecs.set_component(entity, Component.Projectile, Marker.MARKED);
        ecs.set_component(entity, Component.TrailTipColor, tip_color);
        ecs.set_component(entity, Component.TrailTailColor, tail_color);
        ecs.set_component(entity, Component.ProjectileDamage, new MutableFloat(damage));
        ecs.set_component(entity, Component.RenderOrigin, new Vector3f(ray.origin()));
        ecs.set_component(entity, Component.RenderTerminus, new Vector3f(ray.endpoint()));
        ecs.set_component(entity, Component.Lifetime, new MutableDouble(lifetime));
        ecs.set_component(entity, Component.MaxLifetime, new MutableDouble(lifetime));
        ecs.set_component(entity, Component.HitScanTrail, trail_quad);
        ecs.set_component(entity, Component.HitScanTrailRender, trail_quad.copy());
    }

    public static void physics(ECS<Component> ecs,
                               String entity,
                               float mass,
                               float inertia,
                               float drag,
                               float max_thrust,
                               float max_yaw,
                               float max_pitch,
                               float max_roll,
                               float max_speed,
                               float max_ang_speed,
                               Vector3d position,
                               Vector3d rotation,
                               Vector3d scale)
    {
        var hulls = init_hulls(ecs, entity);

        // Core physics components
        ecs.set_component(entity, Component.PhysicsTracked,      Marker.MARKED);
        ecs.set_component(entity, Component.Hulls,               hulls);
        ecs.set_component(entity, Component.Bounds,              new Bounds3d(entity));

        // World-space 3D vector components
        ecs.set_component(entity, Component.Position,            position);
        ecs.set_component(entity, Component.Rotation,            rotation);
        ecs.set_component(entity, Component.Scale,               scale);
        ecs.set_component(entity, Component.PreviousPosition,    new Vector3d(position));
        ecs.set_component(entity, Component.PreviousRotation,    new Vector3d(rotation));
        ecs.set_component(entity, Component.Heading,             new Vector3d());
        ecs.set_component(entity, Component.Acceleration,        new Vector3d());
        ecs.set_component(entity, Component.Velocity,            new Vector3d());

        // scalar components
        ecs.set_component(entity, Component.Mass,                new MutableFloat(mass));
        ecs.set_component(entity, Component.Drag,                new MutableFloat(drag));
        ecs.set_component(entity, Component.Inertia,             new MutableFloat(inertia));
        ecs.set_component(entity, Component.MaxPitch,            new MutableFloat(max_pitch));
        ecs.set_component(entity, Component.MaxRoll,             new MutableFloat(max_roll));
        ecs.set_component(entity, Component.MaxSpeed,            new MutableFloat(max_speed));
        ecs.set_component(entity, Component.MaxAngSpeed,         new MutableFloat(max_ang_speed));
        ecs.set_component(entity, Component.MaxThrust,           new MutableFloat(max_thrust));
        ecs.set_component(entity, Component.MaxYaw,              new MutableFloat(max_yaw));
        ecs.set_component(entity, Component.AngularAcceleration, new MutableFloat());
        ecs.set_component(entity, Component.AngularVelocity,     new MutableFloat());
        ecs.set_component(entity, Component.Thrust,              new MutableFloat());
        ecs.set_component(entity, Component.Yaw,                 new MutableFloat());
    }

    private static MutableConvexHull[] init_hulls(ECS<Component> ecs, String parent_entity)
    {
        var model = Component.Model.<GLTFModel>for_entity(ecs, parent_entity);
        var models = Component.Models.<ModelRegistry<GLTFModel>>global(ecs);
        var hulls = models.get_hulls(model);
        var mutable_hulls = new MutableConvexHull[hulls.length];
        int hull_index = 0;
        for (var hull : hulls)
        {
            var mutable_hull = hull.make_mutable_copy(parent_entity);
            mutable_hull.init();
            mutable_hulls[hull_index++] = mutable_hull;
        }
        return mutable_hulls;
    }

    private static String[] init_lights(ECS<Component> ecs, GLTFModel model, float scale)
    {
        var models = Component.Models.<ModelRegistry<GLTFModel>>global(ecs);
        var lights = models.get(model).lights();
        var model_lights = new String[lights.length];
        int light_index = 0;
        for (var light : lights)
        {
            var light_entity = ecs.new_entity();
            model_lights[light_index++] = light_entity;
            switch (light.type())
            {
                case POINT -> point_light(ecs, light_entity, light, scale);
                case SPOT -> spot_light(ecs, light_entity, light, scale);
            }
        }
        return model_lights;
    }
}
