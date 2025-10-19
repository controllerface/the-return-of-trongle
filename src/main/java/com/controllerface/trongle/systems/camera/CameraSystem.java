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

        camera.set_pitch_range(-90.0f, -90.0f);
        camera.set_yaw_range(0.0f, 0.0f);
        camera.set_zoom_distance_limits(50.0f, 50.0f);

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
