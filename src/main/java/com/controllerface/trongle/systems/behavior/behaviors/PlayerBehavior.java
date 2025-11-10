package com.controllerface.trongle.systems.behavior.behaviors;

import com.juncture.alloy.camera.WorldCamera;
import com.juncture.alloy.data.MutableBoolean;
import com.juncture.alloy.data.MutableDouble;
import com.juncture.alloy.data.MutableFloat;
import com.juncture.alloy.data.MutableInt;
import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.gpu.Window;
import com.juncture.alloy.utils.math.MathEX;
import com.juncture.alloy.utils.math.Ray3d;
import com.controllerface.trongle.components.*;
import com.controllerface.trongle.systems.input.InputBinding;
import com.controllerface.trongle.systems.input.InputState;
import org.joml.*;

import java.lang.Math;
import java.util.ArrayList;
import java.util.List;

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
            velocity.set(0);
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

    private static void handle_mouse_inputs(ECSLayer<Component> ecs, String entity_id, Vector3d position, InputState input_state)
    {
        var mouse_ray_entity = Component.MouseRay.<String>global(ecs);
        var mouse_ray        = Component.RayCast.<Ray3d>for_entity(ecs, mouse_ray_entity);
        var hit_scan_found   = Component.RayCastFound.<MutableBoolean>for_entity(ecs, mouse_ray_entity);
        var ray_cast_hit     = Component.RayCastHit.<Vector3d>for_entity(ecs, mouse_ray_entity);

        var camera_yaw       = Component.CameraYaw.<MutableFloat>global(ecs);
        var camera_pitch     = Component.CameraPitch.<MutableFloat>global(ecs);
        var camera_zoom      = Component.CameraZoom.<MutableFloat>global(ecs);

        handle_aiming(ecs, input_state, mouse_ray);

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

    private static void handle_aiming(ECSLayer<Component> ecs, InputState input_state, Ray3d mouse_ray)
    {
        var window = Component.MainWindow.<Window>global(ecs);
        var camera = Component.MainCamera.<WorldCamera>global(ecs);

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

    public static void behave(double dt, ECSLayer<Component> ecs, String entity_id)
    {
        var input_state   = Component.Input.<InputState>global(ecs);
        var velocity      = Component.Velocity.<Vector3d>for_entity(ecs, entity_id);
        var position      = Component.Position.<Vector3d>for_entity(ecs, entity_id);
        var rotation      = Component.Rotation.<Vector3d>for_entity(ecs, entity_id);
        var heading       = Component.Heading.<Vector3d>for_entity(ecs, entity_id);
        var thrust        = Component.Thrust.<MutableFloat>for_entity(ecs, entity_id);
        var yaw           = Component.Yaw.<MutableFloat>for_entity(ecs, entity_id);
        var ang_velocity  = Component.AngularVelocity.<MutableFloat>for_entity(ecs, entity_id);
        var max_speed     = Component.MaxSpeed.<MutableFloat>for_entity(ecs, entity_id);
        var max_ang_speed = Component.MaxAngSpeed.<MutableFloat>for_entity(ecs, entity_id);
        var max_pitch     = Component.MaxPitch.<MutableFloat>for_entity(ecs, entity_id);
        var max_roll      = Component.MaxRoll.<MutableFloat>for_entity(ecs, entity_id);

        CommonBehavior.zero_for_next_tick(position, thrust, yaw);
        CommonBehavior.update_heading(heading, rotation);
        handle_key_inputs(input_state, thrust, velocity, ang_velocity, yaw, rotation, max_ang_speed);
        handle_mouse_inputs(ecs, entity_id, position, input_state);
        //CommonBehavior.handle_movement_tilt(velocity, heading, rotation, ang_velocity, max_speed, max_ang_speed, max_pitch, max_roll);
    }
}
