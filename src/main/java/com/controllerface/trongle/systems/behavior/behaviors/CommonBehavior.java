package com.controllerface.trongle.systems.behavior.behaviors;

import com.juncture.alloy.data.MutableFloat;
import org.joml.Vector3d;

public class CommonBehavior
{
    public static void handle_movement_tilt(Vector3d velocity,
                                            Vector3d current_heading,
                                            Vector3d rotation,
                                            MutableFloat ang_velocity,
                                            MutableFloat max_speed,
                                            MutableFloat max_ang_speed,
                                            MutableFloat max_pitch_degrees,
                                            MutableFloat max_roll_degrees)
    {
        double signed_speed = velocity.dot(current_heading);
        double speed_ratio = (velocity.length() / max_speed.value);
        double pitch_radians = Math.toRadians(max_pitch_degrees.value * speed_ratio);
        double tilt_factor = Math.clamp(signed_speed / max_speed.value, -1.0, 1.0);
        rotation.x = pitch_radians * tilt_factor;

        double ang_ratio = ang_velocity.value / max_ang_speed.value;
        double roll_radians = -Math.toRadians(max_roll_degrees.value * ang_ratio);
        double smoothing = 0.01;
        rotation.z = (1.0 - smoothing) * rotation.z + smoothing * roll_radians;
    }

    public static void zero_for_next_tick(Vector3d current_position, MutableFloat thrust, MutableFloat yaw)
    {
        current_position.y = 0.0f;
        thrust.value       = 0.0f;
        yaw.value          = 0.0f;
    }

    public static void update_heading(Vector3d current_heading, Vector3d current_rotation)
    {
        current_heading.set(Math.sin(current_rotation.y),0, Math.cos(current_rotation.y));
    }
}
