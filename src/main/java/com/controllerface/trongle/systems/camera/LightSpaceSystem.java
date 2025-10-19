package com.controllerface.trongle.systems.camera;

import com.juncture.alloy.camera.WorldCamera;
import com.juncture.alloy.data.MutableFloat;
import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.ecs.ECSSystem;
import com.juncture.alloy.gpu.gl.textures.GL_ShadowTexture;
import com.controllerface.trongle.components.Component;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class LightSpaceSystem extends ECSSystem<Component>
{
    private static final float ORTHO_FACTOR       = 1.5f;
    private static final float DIR_LIGHT_DISTANCE = 25.0f;

    private final Matrix4f light_space_matrix      = new Matrix4f();
    private final Matrix4f light_view_matrix       = new Matrix4f();
    private final Matrix4f light_projection_matrix = new Matrix4f();
    private final Vector3f light_position          = new Vector3f();
    private final Vector3f light_direction         = new Vector3f();
    private final Vector3f light_magnitude         = new Vector3f();
    private final Vector3f camera_view_center      = new Vector3f();
    private final Vector3f camera_view_front       = new Vector3f();

    private final WorldCamera camera;
    private final Vector3f sun_direction;
    private final Vector3f moon_direction;
    private final MutableFloat time_of_day;

    public LightSpaceSystem(ECS<Component> ecs)
    {
        super(ecs);

        camera = Component.MainCamera.global(ecs);

        ecs.set_global(Component.LightSpaceMatrix, light_space_matrix);

        var player = ecs.get_first_entity(Component.Player);
        assert player != null;

        var sun_light_entity  = ecs.get_first_entity(Component.SunLight);
        var moon_light_entity = ecs.get_first_entity(Component.MoonLight);

        assert sun_light_entity != null;
        assert moon_light_entity != null;

        sun_direction  = Component.Direction.for_entity(ecs, sun_light_entity);
        moon_direction = Component.Direction.for_entity(ecs, moon_light_entity);
        time_of_day    = Component.TimeOfDay.global(ecs);
    }

    private void update_light_space_matrix()
    {
        var light_source = time_of_day.value < 0.5f
            ? sun_direction
            : moon_direction;

        light_direction.set(light_source).normalize();

        camera.front()
            .mul(camera.radius(), camera_view_front);

        camera_view_center.set(camera.position())
            .add(camera_view_front);

        float ortho_half_size = Math.max(camera.radius() * ORTHO_FACTOR, 15.0f);

        float texelSize = (ortho_half_size * 2.0f) / GL_ShadowTexture.SHADOW_MAP_RESOLUTION;

        camera_view_center.x = (float) Math.floor(camera_view_center.x / texelSize) * texelSize;
        camera_view_center.y = (float) Math.floor(camera_view_center.y / texelSize) * texelSize;
        camera_view_center.z = (float) Math.floor(camera_view_center.z / texelSize) * texelSize;

        light_magnitude.set(light_direction)
            .mul(DIR_LIGHT_DISTANCE);

        light_position.set(camera_view_center)
            .sub(light_magnitude);

        light_view_matrix.identity();
        light_view_matrix.lookAt(light_position, camera_view_center, camera.up_vector());

        light_projection_matrix.identity();
        light_projection_matrix.ortho(
            -ortho_half_size, ortho_half_size,
            -ortho_half_size, ortho_half_size,
            1.0f, 300.0f
        );

        light_space_matrix.set(light_projection_matrix)
            .mul(light_view_matrix);
    }

    @Override
    public void tick(double dt)
    {
        update_light_space_matrix();
    }
}
