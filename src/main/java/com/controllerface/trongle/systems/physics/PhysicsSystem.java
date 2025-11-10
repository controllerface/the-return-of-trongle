package com.controllerface.trongle.systems.physics;

import com.juncture.alloy.data.MutableBoolean;
import com.juncture.alloy.data.MutableDouble;
import com.juncture.alloy.data.MutableFloat;
import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.ecs.ECSSystem;
import com.juncture.alloy.physics.algorithms.GJK;
import com.juncture.alloy.physics.algorithms.MTA;
import com.juncture.alloy.physics.bvh.PhysicsTree;
import com.juncture.alloy.physics.data.Intersection;
import com.juncture.alloy.utils.math.Bounds3d;
import com.juncture.alloy.utils.math.GeometryExtents;
import com.juncture.alloy.utils.math.MutableConvexHull;
import com.juncture.alloy.utils.math.Ray3d;
import com.controllerface.trongle.components.Component;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;

public class PhysicsSystem extends ECSSystem<Component>
{
    public static final float TARGET_FPS = 60.0f;
    public static final int TARGET_SUB_STEPS = 8;
    public static final float FIXED_TIME_STEP = 1.0f / TARGET_FPS / TARGET_SUB_STEPS;

    private double accumulator = 0;

    private final Vector3d phys_intersection_buffer = new Vector3d();
    private final Vector3d phys_closet_hit_buffer = new Vector3d();
    private final Vector3d phys_normal_buffer = new Vector3d();
    private final Vector3d phys_a_buffer = new Vector3d();
    private final Vector3d phys_b_buffer = new Vector3d();
    private final Matrix4d phys_matrix_buffer = new Matrix4d();

    private final GeometryExtents world_extents = new GeometryExtents();
    private final GeometryExtents model_extents = new GeometryExtents();
    private final GeometryExtents mesh_extents = new GeometryExtents();

    private final Bounds3d octree_bounds = new Bounds3d();
    private final Queue<Bounds3d> tree_queue = new ArrayDeque<>();
    private final Queue<CollisionCandidate> narrow_phase_queue = new ArrayDeque<>();

    private final MutableDouble simulation_remainder;

    private PhysicsTree octree;

    private record CollisionCandidate(MutableConvexHull a, MutableConvexHull b)
    {
    }

    public PhysicsSystem(ECSLayer<Component> _ecs)
    {
        super(_ecs);
        simulation_remainder = Component.SimulationRemainder.global(ecs);
    }

    private void integrate_entity(String entity)
    {
        var thrust           = Component.Thrust.<MutableFloat>for_entity(ecs, entity);
        var yaw              = Component.Yaw.<MutableFloat>for_entity(ecs, entity);
        var mass             = Component.Mass.<MutableFloat>for_entity(ecs, entity);
        var inertia          = Component.Inertia.<MutableFloat>for_entity(ecs, entity);
        var drag             = Component.Drag.<MutableFloat>for_entity(ecs, entity);
        var max_thrust       = Component.MaxThrust.<MutableFloat>for_entity(ecs, entity);
        var max_yaw          = Component.MaxYaw.<MutableFloat>for_entity(ecs, entity);
        var velocity         = Component.Velocity.<Vector3d>for_entity(ecs, entity);
        var ang_velocity     = Component.AngularVelocity.<MutableFloat>for_entity(ecs, entity);
        var acceleration     = Component.Acceleration.<Vector3d>for_entity(ecs, entity);
        var ang_acceleration = Component.AngularAcceleration.<MutableFloat>for_entity(ecs, entity);
        var position         = Component.Position.<Vector3d>for_entity(ecs, entity);
        var rotation         = Component.Rotation.<Vector3d>for_entity(ecs, entity);
        var prv_position     = Component.PreviousPosition.<Vector3d>for_entity(ecs, entity);
        var prv_rotation     = Component.PreviousRotation.<Vector3d>for_entity(ecs, entity);

        prv_position.set(position);
        prv_rotation.set(rotation);

        float torque = yaw.value * max_yaw.value;
        ang_acceleration.value = torque / inertia.value;
        ang_velocity.value += ang_acceleration.value * FIXED_TIME_STEP;
        ang_velocity.value *= (1 - drag.value * FIXED_TIME_STEP);
        rotation.y += ang_velocity.value * FIXED_TIME_STEP;

        float force = thrust.value * max_thrust.value;
        double force_x = Math.sin(rotation.y) * force;
        double force_z = Math.cos(rotation.y) * force;
        acceleration.set(force_x / mass.value, 0.0f, force_z / mass.value);
        acceleration.mul(FIXED_TIME_STEP);

        velocity.mul(1 - drag.value * FIXED_TIME_STEP);
        velocity.add(acceleration);

        position.add(
            velocity.x * FIXED_TIME_STEP,
            velocity.y * FIXED_TIME_STEP,
            velocity.z * FIXED_TIME_STEP
        );

        update_bounding_geometry(entity);
    }

    private final Quaterniond rotation_buffer = new Quaterniond();


    private double clamp_abs_value(double value, double bound)
    {
        bound = Math.abs(bound);
        if (value > bound || value < -bound)
        {
            return value > 0 ? bound : -bound;
        }
        return value;
    }

    private void update_bounding_geometry(String entity)
    {
        var position = Component.Position.<Vector3d>for_entity(ecs, entity);
        var rotation = Component.Rotation.<Vector3d>for_entity(ecs, entity);
        var scale    = Component.Scale.<Vector3d>for_entity(ecs, entity);
        var bounds   = Component.Bounds.<Bounds3d>for_entity(ecs, entity);

        rotation_buffer.identity();
        rotation_buffer.rotateXYZ(rotation.x, rotation.y, rotation.z);

        phys_matrix_buffer.identity();
        phys_matrix_buffer.translate(position);
        phys_matrix_buffer.rotateYXZ(rotation); // important: must rotate in YXZ order
        phys_matrix_buffer.scale(scale);

        var hulls = Component.Hulls.<MutableConvexHull[]>for_entity(ecs, entity);
        model_extents.reset();
        for (var hull : hulls)
        {
            hull.transform(phys_matrix_buffer, world_extents, model_extents, mesh_extents);
        }
        bounds.update(model_extents);
        tree_queue.add(bounds);
    }

    private void coarse_collide_entity(String entity)
    {
        var bounds = Component.Bounds.<Bounds3d>for_entity(ecs, entity);
        var hits = octree.query(bounds);

        var a_hulls = Component.Hulls.<MutableConvexHull[]>for_entity(ecs, entity);
        for (Bounds3d hit : hits)
        {
            var b_hulls = Component.Hulls.<MutableConvexHull[]>for_entity(ecs, hit.entity);
            for (var hull_a : a_hulls)
            {
                for (var hull_b : b_hulls)
                {
                    if (hull_a.bounds.intersects(hull_b.bounds))
                    {
                        narrow_phase_queue.add(new CollisionCandidate(hull_a, hull_b));
                    }
                }
            }
        }
    }

    private void narrow_collide_candidate(CollisionCandidate candidate)
    {
        candidate.b.centroid.sub(candidate.a.centroid, phys_normal_buffer);
        var intersection = GJK.find_intersection(candidate.a.vertices, candidate.b.vertices, phys_normal_buffer);
        if (intersection.result() == Intersection.State.INTERSECTING)
        {
            var position_a     = Component.Position.<Vector3d>for_entity(ecs, candidate.a.parent_entity);
            var position_b     = Component.Position.<Vector3d>for_entity(ecs, candidate.b.parent_entity);
            var velocity_a     = Component.Velocity.<Vector3d>for_entity(ecs, candidate.a.parent_entity);
            var velocity_b     = Component.Velocity.<Vector3d>for_entity(ecs, candidate.b.parent_entity);
            var ang_velocity_a = Component.AngularVelocity.<MutableFloat>for_entity(ecs, candidate.a.parent_entity);
            var ang_velocity_b = Component.AngularVelocity.<MutableFloat>for_entity(ecs, candidate.b.parent_entity);
            var mass_a         = Component.Mass.<MutableFloat>for_entity(ecs, candidate.a.parent_entity);
            var mass_b         = Component.Mass.<MutableFloat>for_entity(ecs, candidate.b.parent_entity);
            var inertia_a      = Component.Inertia.<MutableFloat>for_entity(ecs, candidate.a.parent_entity);
            var inertia_b      = Component.Inertia.<MutableFloat>for_entity(ecs, candidate.b.parent_entity);

            float restitution = 0.8f; // todo: define material types an build lookup table for defining restitution

            double total_mass = mass_a.value + mass_b.value;
            double magnitude_a = mass_b.value / total_mass;
            double magnitude_b = mass_a.value / total_mass;

            var half_depth = intersection.contact().depth() / 2;

            phys_normal_buffer.set(intersection.contact().normal()).negate();
            phys_normal_buffer.mul(half_depth * magnitude_a);
            position_a.add(phys_normal_buffer);

            phys_normal_buffer.set(intersection.contact().normal());
            phys_normal_buffer.mul(half_depth * magnitude_b);
            position_b.add(phys_normal_buffer);

            velocity_b.sub(velocity_a, phys_normal_buffer);

            double velAlongNormal = phys_normal_buffer.dot(intersection.contact().normal());

            if (velAlongNormal > 0)
            {
                return; // Objects are separating, no need to apply impulse
            }

            double impulseMagnitude = -(1.0 + restitution) * velAlongNormal;
            impulseMagnitude /= (1.0 / mass_a.value) + (1.0 / mass_b.value);

            phys_normal_buffer.set(intersection.contact().normal());
            phys_normal_buffer.mul(impulseMagnitude);

            phys_normal_buffer.mul(1.0 / mass_a.value, phys_a_buffer);
            velocity_a.sub(phys_a_buffer);

            phys_normal_buffer.mul(1.0 / mass_b.value, phys_b_buffer);
            velocity_b.add(phys_b_buffer);

            phys_a_buffer.set(intersection.contact().point());
            phys_a_buffer.sub(position_a);

            phys_b_buffer.set(intersection.contact().point());
            phys_b_buffer.sub(position_b);

            double torqueA = phys_a_buffer.y * phys_normal_buffer.x - phys_a_buffer.x * phys_normal_buffer.y;
            double torqueB = phys_b_buffer.y * phys_normal_buffer.x - phys_b_buffer.x * phys_normal_buffer.y;

            if (inertia_a.value > 0)
            {
                double angularChangeA = (torqueA / inertia_a.value);
                angularChangeA = clamp_abs_value(angularChangeA, 1);
                ang_velocity_a.value += (float) angularChangeA;
            }

            if (inertia_b.value > 0)
            {
                double angularChangeB = torqueB / inertia_b.value;
                angularChangeB = clamp_abs_value(angularChangeB, 1);
                ang_velocity_b.value += (float) angularChangeB;
            }
        }
    }

    private void hit_scan_collide()
    {
        var rays = ecs.get_components(Component.RayCast);
        for (var ray_entry : rays.entrySet())
        {
            var ray_entity = ray_entry.getKey();
            var ray_cast = Component.RayCast.<Ray3d>coerce(ray_entry.getValue());

            boolean mouse_collider = ecs.has_component(ray_entity, Component.MouseCollider);
            boolean interact       = Component.RayCastInteract.for_entity(ecs, ray_entity);
            var hit_scan_complete  = Component.RayCastComplete.<MutableBoolean>for_entity(ecs, ray_entity);
            var hit_scan_found     = Component.RayCastFound.<MutableBoolean>for_entity(ecs, ray_entity);
            var ray_cast_hit       = Component.RayCastHit.<Vector3d>for_entity(ecs, ray_entity);

            if (!mouse_collider && hit_scan_complete.value) continue;

            var hits = octree.query(ray_cast);

            String hit_entity = null;
            double closest = Double.POSITIVE_INFINITY;
            for (var hit : hits)
            {
                var hulls = Component.Hulls.<MutableConvexHull[]>for_entity_or_null(ecs, hit.entity);
                if (hulls == null) continue;
                for (var hull : hulls)
                {
                    boolean hull_hit = hull.bounds.intersects(ray_cast);
                    if (hull_hit)
                    {
                        var distance = MTA.find_intersection(ray_cast, hull, phys_intersection_buffer);
                        if (distance < closest)
                        {
                            closest = distance;
                            phys_closet_hit_buffer.set(phys_intersection_buffer);
                            hit_entity = hit.entity;
                        }
                    }
                }
            }

            if (hit_entity != null)
            {
                hit_scan_found.value = true;
                ray_cast_hit.set(phys_closet_hit_buffer);
                ecs.set_component(ray_entity, Component.HitScanResult, hit_entity);

                if (interact)
                {
                    var position     = Component.Position.<Vector3d>for_entity(ecs, hit_entity);
                    var velocity     = Component.Velocity.<Vector3d>for_entity(ecs, hit_entity);
                    var ang_velocity = Component.AngularVelocity.<MutableFloat>for_entity(ecs, hit_entity);
                    var mass         = Component.Mass.<MutableFloat>for_entity(ecs, hit_entity);
                    var inertia      = Component.Inertia.<MutableFloat>for_entity(ecs, hit_entity);

                    float impulseMagnitude = 20.0f; // Hardcoded impact strength
                    float angularImpulse = 100.0f; // Hardcoded rotational effect

                    phys_normal_buffer.set(ray_cast.direction());
                    phys_normal_buffer.mul(impulseMagnitude / mass.value);

                    velocity.add(phys_normal_buffer);

                    var contact_point = new Vector3d(ray_cast_hit);
                    var offset = new Vector3d(contact_point).sub(position);

                    double torque = offset.y * phys_normal_buffer.x - offset.x * phys_normal_buffer.y;
                    if (inertia.value > 0)
                    {
                        ang_velocity.value += (float) (torque / inertia.value) * -angularImpulse;
                    }
                }

                // NOTE: do not update bounding geometry, even though a change in position occurs, because the tree
                // is not rebuilt after this step. It can result in assertion on collision when object collides as
                // it becomes destroyed in the next frame.
            }
            else
            {
                hit_scan_found.value = false;
            }

            hit_scan_complete.value = true;
        }
    }

    private void integrate(Set<String> tracked_entities)
    {
        world_extents.reset();
        for (var entity : tracked_entities)
        {
            integrate_entity(entity);
        }
        octree_bounds.update(world_extents);

        // todo: figure out a way to reset/reuse the tree components to reduce memory load
        octree = new PhysicsTree(octree_bounds, tree_queue, 32, 8);
        ecs.set_global(Component.CollisionBVH, octree);
    }

    private void collide(Set<String> tracked_entities)
    {
        for (var entity : tracked_entities)
        {
            coarse_collide_entity(entity);
        }
        CollisionCandidate candidate;
        while ((candidate = narrow_phase_queue.poll()) != null)
        {
            narrow_collide_candidate(candidate);
        }
    }

    private void simulate(Set<String> tracked_entities, double dt)
    {
        accumulator += dt;
        while (accumulator > 0)
        {
            integrate(tracked_entities);
            collide(tracked_entities);
            accumulator -= FIXED_TIME_STEP;
            if (accumulator <= 0) hit_scan_collide();
        }
    }

    @Override
    public void tick(double dt)
    {
        var tracked_entities = ecs.get_components(Component.PhysicsTracked).keySet();
        simulate(tracked_entities, dt);
        simulation_remainder.value = 1 + accumulator / FIXED_TIME_STEP;
    }
}
