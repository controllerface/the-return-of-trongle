package com.controllerface.trongle.systems.behavior;

import com.juncture.alloy.camera.WorldCamera;
import com.juncture.alloy.data.MutableBoolean;
import com.juncture.alloy.data.MutableFloat;
import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.gpu.Window;
import com.juncture.alloy.physics.PhysicsComponent;
import com.juncture.alloy.rendering.RenderComponent;
import com.juncture.alloy.utils.math.MathEX;
import com.juncture.alloy.utils.math.Ray3d;
import com.controllerface.trongle.components.*;
import com.controllerface.trongle.systems.input.InputBinding;
import com.controllerface.trongle.systems.input.InputState;
import org.joml.*;

import java.lang.Math;

public class PlayerBehavior
{
    // todo: figure out best approach for possible future multi-threading friendly approach
    private static final Vector2d delta_buffer       = new Vector2d();
    private static final Vector3d target_heading     = new Vector3d();
    private static final Vector4d target_vec4_buffer = new Vector4d();
    private static final Vector3d target_vec3_buffer = new Vector3d();
    private static final Matrix4d target_mat4_buffer = new Matrix4d();
    private static final Vector2f input_buffer       = new Vector2f();


    private static void handle_key_inputs(InputState input_state,
                                          MutableFloat thrust,
                                          Vector3d velocity,
                                          MutableFloat ang_velocity,
                                          MutableFloat yaw,
                                          Vector3d rotation,
                                          MutableFloat max_ang_speed)
    {
        input_buffer.set(0.0f);

        boolean left  = input_state.is_active(InputBinding.MOVE_LEFT);
        boolean right = input_state.is_active(InputBinding.MOVE_RIGHT);

        if (left)     input_buffer.x += 1f;
        if (right)    input_buffer.x -= 1f;

        var idle = (!left && !right);

        if (input_buffer.lengthSquared() > 0f)
        {
            input_buffer.normalize();
        }

        if (input_buffer.y == 0.0 && input_buffer.x == 0.0)
        {
            thrust.value = 0.0f;
            velocity.mul(.998);
        }
        else
        {
            thrust.value = 1.0f;
        }

        double targetYaw = Math.atan2(input_buffer.x, input_buffer.y);
        if (idle) targetYaw += Math.PI;

        double currentYaw = rotation.y; // In radians
        double yawDelta = MathEX.normalizeAngle(targetYaw - currentYaw);

        double yawThreshold = 0.05f; // radians, how closely aligned we must be to consider "facing"

        if (Math.abs(yawDelta) > yawThreshold)
        {
            yaw.value = (float) Math.signum(yawDelta) * (max_ang_speed.value);
        }
        else
        {
            yaw.value = 0f;
            ang_velocity.value = 0f;
            rotation.y = targetYaw;
        }
    }

    private static void handle_mouse_inputs(ECSLayer<Component> ecs,
                                            ECSLayer<PhysicsComponent> pecs,
                                            ECSLayer<RenderComponent> recs,
                                            String entity_id,
                                            Vector3d position,
                                            InputState input_state)
    {
        var mouse_ray_entity = PhysicsComponent.MouseRay.<String>global(pecs);
        var mouse_ray        = PhysicsComponent.RayCast.<Ray3d>for_entity(pecs, mouse_ray_entity);
        var hit_scan_found   = PhysicsComponent.RayCastFound.<MutableBoolean>for_entity(pecs, mouse_ray_entity);
        var ray_cast_hit     = PhysicsComponent.RayCastHit.<Vector3d>for_entity(pecs, mouse_ray_entity);

        var camera_yaw       = RenderComponent.CameraYaw.<MutableFloat>global(recs);
        var camera_pitch     = RenderComponent.CameraPitch.<MutableFloat>global(recs);
        var camera_zoom      = RenderComponent.CameraZoom.<MutableFloat>global(recs);

        handle_aiming(ecs, recs, input_state, mouse_ray);

        double scroll = input_state.get_scroll();
        if (scroll != 0)
        {
            camera_zoom.value = (float) scroll;
            input_state.mouse_scroll_reset();
        }

        if (input_state.is_active(InputBinding.CAMERA_ADJUST))
        {
            input_state.calculate_mouse_delta(delta_buffer);
            input_state.mouse_pos_reset();
            camera_yaw.value = (float) delta_buffer.x;
            camera_pitch.value = (float) delta_buffer.y;
        }
    }

    private static void handle_aiming(ECSLayer<Component> ecs,
                                      ECSLayer<RenderComponent> recs,
                                      InputState input_state,
                                      Ray3d mouse_ray)
    {
        var window = RenderComponent.MainWindow.<Window>global(recs);
        var camera = RenderComponent.MainCamera.<WorldCamera>global(recs);

        var mouse_pos = input_state.get_mouse_pos();

        // Convert to NDC/Clip Space
        float xNdc = (float) ((2.0f * mouse_pos.x()) / window.width() - 1.0f);
        float yNdc = (float) (1.0f - (2.0f * mouse_pos.y()) / window.height());
        target_vec4_buffer.set(xNdc, yNdc, -1.0f, 1.0f);

        // Convert to View Space
        target_mat4_buffer.set(camera.projection_matrix()).invert();
        target_mat4_buffer.transform(target_vec4_buffer);
        target_vec3_buffer.set(target_vec4_buffer).normalize();

        // Convert to World Space
        target_mat4_buffer.set(camera.view_matrix()).invert();
        target_vec4_buffer.set(target_vec3_buffer, 0.0f);
        target_mat4_buffer.transform(target_vec4_buffer);
        target_vec3_buffer.set(target_vec4_buffer).normalize();

        // Update mouse ray
        mouse_ray.direction().set(target_vec3_buffer);
        mouse_ray.origin().set(camera.position());
        mouse_ray.endpoint().set(camera.position()).add(target_vec3_buffer.mul(1000));
        mouse_ray.endpoint().sub(mouse_ray.origin(), target_vec3_buffer);

        target_vec3_buffer.x = (target_vec3_buffer.x != 0.0f)
            ? (1.0f / target_vec3_buffer.x)
            : Double.POSITIVE_INFINITY;

        target_vec3_buffer.y = (target_vec3_buffer.y != 0.0f)
            ? (1.0f / target_vec3_buffer.y)
            : Double.POSITIVE_INFINITY;

        target_vec3_buffer.z = (target_vec3_buffer.z != 0.0f)
            ? (1.0f / target_vec3_buffer.z)
            : Double.POSITIVE_INFINITY;

        mouse_ray.inv_direction().set(target_vec3_buffer);
    }

    public static void behave(double dt,
                              ECSLayer<Component> ecs,
                              ECSLayer<PhysicsComponent> pecs,
                              ECSLayer<RenderComponent> recs,
                              String entity_id)
    {
        var input_state   = Component.Input.<InputState>global(ecs);
        var velocity      = PhysicsComponent.Velocity.<Vector3d>for_entity(pecs, entity_id);
        var position      = PhysicsComponent.Position.<Vector3d>for_entity(pecs, entity_id);
        var rotation      = PhysicsComponent.Rotation.<Vector3d>for_entity(pecs, entity_id);
        var heading       = PhysicsComponent.Heading.<Vector3d>for_entity(pecs, entity_id);
        var thrust        = PhysicsComponent.Thrust.<MutableFloat>for_entity(pecs, entity_id);
        var yaw           = PhysicsComponent.Yaw.<MutableFloat>for_entity(pecs, entity_id);
        var ang_velocity  = PhysicsComponent.AngularVelocity.<MutableFloat>for_entity(pecs, entity_id);
        var max_speed     = PhysicsComponent.MaxSpeed.<MutableFloat>for_entity(pecs, entity_id);
        var max_ang_speed = PhysicsComponent.MaxAngSpeed.<MutableFloat>for_entity(pecs, entity_id);
        var max_pitch     = PhysicsComponent.MaxPitch.<MutableFloat>for_entity(pecs, entity_id);
        var max_roll      = PhysicsComponent.MaxRoll.<MutableFloat>for_entity(pecs, entity_id);

        CommonBehavior.zero_for_next_tick(position, thrust, yaw);
        CommonBehavior.update_heading(heading, rotation);
        handle_key_inputs(input_state, thrust, velocity, ang_velocity, yaw, rotation, max_ang_speed);
        handle_mouse_inputs(ecs, pecs, recs, entity_id, position, input_state);
        //CommonBehavior.handle_movement_tilt(velocity, heading, rotation, ang_velocity, max_speed, max_ang_speed, max_pitch, max_roll);
    }
}
