package com.controllerface.trongle.systems.camera;

import com.juncture.alloy.camera.WorldCamera;
import com.juncture.alloy.data.MutableFloat;
import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.ecs.ECSSystem;
import com.juncture.alloy.gpu.Window;
import com.controllerface.trongle.components.Component;
import org.joml.Vector3f;

public class CameraSystem extends ECSSystem<Component>
{
    private static final float CENTER_PITCH = -90f;
    private static final float CENTER_YAW   =   0f;
    private static final float PITCH_RANGE  =  15f;
    private static final float YAW_RANGE    =  25f;
    private static final float ZOOM_MIN     =  25f;
    private static final float ZOOM_MAX     =  50f;
    private static final float ZOOM_SPEED   =  10.0f;
    private static final float ZOOM_RATE    =  0.001f;

    private final MutableFloat delta_yaw      = new MutableFloat(0);
    private final MutableFloat delta_pitch    = new MutableFloat(0);
    private final MutableFloat delta_zoom     = new MutableFloat(0);

    private final Vector3f player_position;
    private final WorldCamera camera;
    private final Window window;

    public CameraSystem(ECS<Component> ecs)
    {
        super(ecs);

        window = Component.MainWindow.global(ecs);
        camera = Component.MainCamera.global(ecs);
        ecs.set_global(Component.CameraYaw, delta_yaw);
        ecs.set_global(Component.CameraPitch, delta_pitch);
        ecs.set_global(Component.CameraZoom, delta_zoom);

        camera.set_pitch_range(CENTER_PITCH - PITCH_RANGE, CENTER_PITCH + PITCH_RANGE);
        camera.set_yaw_range(CENTER_YAW - YAW_RANGE, CENTER_YAW + YAW_RANGE);
        camera.set_zoom_speed_limits(ZOOM_SPEED, ZOOM_RATE);
        camera.set_zoom_distance_limits(ZOOM_MIN, ZOOM_MAX);

        camera.set_pitch(CENTER_PITCH);
        camera.set_yaw(CENTER_YAW);

        var player = ecs.get_first_entity(Component.Player);
        assert player != null;
        player_position = Component.RenderPosition.for_entity(ecs, player);
    }

    @Override
    public void tick(double dt)
    {
        camera.adjust_follow_target(player_position);
        if (delta_yaw.value != 0 || delta_pitch.value != 0)
        {
            camera.adjust_pitch_and_yaw(delta_yaw.value, delta_pitch.value);
            delta_yaw.value = 0;
            delta_pitch.value = 0;
        }
        if (delta_zoom.value != 0)
        {
            camera.adjust_follow_distance(delta_zoom.value);
            delta_zoom.value = 0;
        }
        camera.adjust_projection(window.height(), window.width());
    }
}
