package com.controllerface.trongle;

import com.controllerface.trongle.render.GLTFModel;
import com.juncture.alloy.camera.WorldCamera;
import com.juncture.alloy.data.LightEmitterType;
import com.juncture.alloy.data.MutableDouble;
import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.ecs.ECSSystem;
import com.juncture.alloy.gpu.RenderSet;
import com.juncture.alloy.models.ModelRegistry;
import com.juncture.alloy.physics.bvh.RenderTree;
import com.juncture.alloy.utils.math.AABB;
import com.juncture.alloy.utils.math.Bounds3f;
import com.juncture.alloy.utils.math.RenderExtents;
import org.joml.*;

import java.util.ArrayDeque;
import java.util.Queue;

public class TransformUpdateSystem extends ECSSystem<Component>
{
    private final float[] ref_min = new float[3];
    private final float[] ref_max = new float[3];
    private final float[] out_min = new float[3];
    private final float[] out_max = new float[3];
    private final float[] raw_matrix = new float[16];

    private final Vector4f l_buf = new Vector4f();
    private final Matrix3f m_buf = new Matrix3f();

    private final MutableDouble simulation_remainder;

    private final ModelRegistry<GLTFModel> models;

    private final RenderExtents render_extents      = new RenderExtents();
    private final Bounds3f         render_bounds       = new Bounds3f();
    private final Queue<Bounds3f>  render_bounds_queue = new ArrayDeque<>();

    private final WorldCamera camera;
    private final RenderSet render_set = new RenderSet();

    public TransformUpdateSystem(ECS<Component> _ecs)
    {
        super(_ecs);
        this.camera = Component.MainCamera.global(ecs);
        this.models = Component.Models.global(ecs);
        simulation_remainder = Component.SimulationRemainder.global(ecs);
        ecs.set_global(Component.RenderVisible, render_set);
    }

    void update_bounds(Matrix4f transform, Vector3f position, AABB ref_bounds, Bounds3f bounds)
    {
        ref_min[0] = ref_bounds.min_x();
        ref_min[1] = ref_bounds.min_y();
        ref_min[2] = ref_bounds.min_z();
        ref_max[0] = ref_bounds.max_x();
        ref_max[1] = ref_bounds.max_y();
        ref_max[2] = ref_bounds.max_z();

        out_min[0] = out_max[0] = position.x;
        out_min[1] = out_max[1] = position.y;
        out_min[2] = out_max[2] = position.z;

        transform.get(raw_matrix);

        for (int col = 0; col < 3; col++)
        {
            for (int row = 0; row < 3; row++)
            {
                float x = raw_matrix[col * 4 + row];
                float a = x * ref_min[col];
                float b = x * ref_max[col];
                if (a < b)
                {
                    out_min[row] += a;
                    out_max[row] += b;
                }
                else
                {
                    out_min[row] += b;
                    out_max[row] += a;
                }
            }
        }

        bounds.min.set(out_min[0], out_min[1], out_min[2]);
        bounds.max.set(out_max[0], out_max[1], out_max[2]);
        bounds.update_derived();
        render_extents.process(bounds);
        render_bounds_queue.add(bounds);
    }

    private void interpolate_transform(String entity,
                                       Vector3f render_position,
                                       Vector3f render_rotation,
                                       Vector3f render_scale,
                                       double alpha, double minus_alpha)
    {
        var position     = Component.Position.<Vector3d>for_entity(ecs, entity);
        var rotation     = Component.Rotation.<Vector3d>for_entity(ecs, entity);
        var scale        = Component.Scale.<Vector3d>for_entity(ecs, entity);
        var prv_position = Component.PreviousPosition.<Vector3d>for_entity(ecs, entity);
        var prv_rotation = Component.PreviousRotation.<Vector3d>for_entity(ecs, entity);

        render_position.set(
            position.x * alpha + prv_position.x * minus_alpha,
            position.y * alpha + prv_position.y * minus_alpha,
            position.z * alpha + prv_position.z * minus_alpha
        );

        render_rotation.set(
            rotation.x * alpha + prv_rotation.x * minus_alpha,
            rotation.y * alpha + prv_rotation.y * minus_alpha,
            rotation.z * alpha + prv_rotation.z * minus_alpha
        );

        // not currently simulating scale mutations, so just pass-thru
        render_scale.set(scale);
    }

    private void update_light_transforms(Matrix4f transform, String[] model_lights, GLTFModel model)
    {
        var base_lights = models.get(model).lights();
        for (int light_index = 0; light_index < model_lights.length; light_index++)
        {
            var base_light = base_lights[light_index];
            var light_entity = model_lights[light_index];
            var light_position = Component.RenderPosition.<Vector3f>for_entity(ecs, light_entity);
            var emitter = Component.Light.<LightEmitterType>for_entity(ecs, light_entity);
            l_buf.set(base_light.data().position(), 1.0f);
            transform.transform(l_buf);
            light_position.set(l_buf);
            if (emitter == LightEmitterType.SPOT)
            {
                transform.get3x3(m_buf);
                var light_direction = Component.Direction.<Vector3f>for_entity(ecs, light_entity);
                m_buf.transform(base_light.data().direction(), light_direction);
                light_direction.normalize();
            }
        }
    }

    private void update_turret_transforms(Matrix4f transform, String[] model_turrets, GLTFModel model)
    {
        var base_cameras = models.get(model).cameras();
        for (int turret_index = 0; turret_index < model_turrets.length; turret_index++)
        {
            var base_camera = base_cameras[turret_index];
            var turret_entity = model_turrets[turret_index];
            var turret_position = Component.RenderPosition.<Vector3f>for_entity(ecs, turret_entity);
            l_buf.set(base_camera.position(), 1.0f);
            transform.transform(l_buf);
            turret_position.set(l_buf);
        }
    }

    private void update_entity_transform(String entity, double simulation_remainder, double negative_remainder)
    {
        var position = Component.RenderPosition.<Vector3f>for_entity(ecs, entity);
        var rotation = Component.RenderRotation.<Vector3f>for_entity(ecs, entity);
        var scale    = Component.RenderScale.<Vector3f>for_entity(ecs, entity);

        var has_physics = Component.PhysicsTracked.for_entity_or_null(ecs, entity);
        if (has_physics != null)
        {
            interpolate_transform(entity, position, rotation, scale, simulation_remainder, negative_remainder);
        }

        var transform = Component.Transform.<Matrix4f>for_entity(ecs, entity);

        transform.identity();
        transform.translate(position);
        transform.rotateYXZ(rotation); // important: must rotate in YXZ order
        transform.scale(scale);

        var model  = Component.Model.<GLTFModel>for_entity(ecs, entity);
        var bounds = Component.RenderBounds.<Bounds3f>for_entity(ecs, entity);

        var ref_bounds = models.get(model).bounds();
        update_bounds(transform, position, ref_bounds, bounds);

        var model_lights = Component.ModelLights.<String[]>for_entity_or_null(ecs, entity);
        if (model_lights != null)
        {
            update_light_transforms(transform, model_lights, model);
        }

        var model_turrets = Component.Turrets.<String[]>for_entity_or_null(ecs, entity);
        if (model_turrets != null)
        {
            update_turret_transforms(transform, model_turrets, model);
        }
    }

    @Override
    public void tick(double dt)
    {
        render_bounds_queue.clear();
        render_extents.reset();
        double negative_remainder = 1.0 - simulation_remainder.value;
        var model_entities = ecs.get_components(Component.Model).keySet();
        for (var entity : model_entities)
        {
            update_entity_transform(entity, simulation_remainder.value, negative_remainder);
        }
        render_bounds.update(render_extents);
        var render_tree = new RenderTree(render_bounds, render_bounds_queue, 16, 8);
        var visible_bounds = render_tree.query(camera.frustum());
        render_set.update(visible_bounds);
        ecs.set_global(Component.RenderBVH, render_tree);
    }
}
