package com.controllerface.trongle.systems.behavior;

import com.juncture.alloy.data.MutableFloat;
import org.joml.Vector3d;

public class CommonBehavior
{

    public static void zero_for_next_tick(Vector3d current_position, MutableFloat thrust, MutableFloat yaw)
    {
        current_position.z = 0.0f;
        thrust.value       = 0.0f;
        yaw.value          = 0.0f;
    }

    public static void update_heading(Vector3d current_heading, Vector3d current_rotation)
    {
        current_heading.set(Math.sin(current_rotation.y),0, Math.cos(current_rotation.y));
    }
}
