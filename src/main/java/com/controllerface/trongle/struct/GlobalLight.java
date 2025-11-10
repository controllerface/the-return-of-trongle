package com.controllerface.trongle.struct;

import com.juncture.alloy.data.LightIntensity;
import com.juncture.alloy.data.MutableFloat;
import com.juncture.alloy.data.MutableInt;
import com.juncture.alloy.ecs.ECSLayer;
import com.controllerface.trongle.components.Component;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

import static com.juncture.alloy.utils.memory.Structs.*;
import static java.lang.foreign.MemoryLayout.structLayout;

/// Uniform data block for global light information
///
/// see: [GlobalLight.glsl](src/main/resources/shaders/_layout/GlobalLight.glsl)
///
public class GlobalLight
{
    public static MemoryLayout LAYOUT = structLayout(
        DirectionalLight.LAYOUT.withName("sun"),
        DirectionalLight.LAYOUT.withName("moon"),
        of_float("blend"),
        of_int("point_light_count"),
        of_int("spot_light_count"));

    private static final long sun_offset  = offset(LAYOUT, "sun");
    private static final long moon_offset = offset(LAYOUT, "moon");

    private static final VarHandle blend             = var_handle(LAYOUT, "blend");
    private static final VarHandle point_light_count = var_handle(LAYOUT, "point_light_count");
    private static final VarHandle spot_light_count  = var_handle(LAYOUT, "spot_light_count");

    public static void ecs_map_at_index(MemorySegment light_segment, int index, ECSLayer<Component> ecs)
    {
        var sun_light_entity = ecs.get_first_entity(Component.SunLight);
        var moon_light_entity = ecs.get_first_entity(Component.MoonLight);

        assert sun_light_entity != null;
        assert moon_light_entity != null;

        long offset = index * LAYOUT.byteSize();

        var sun_color      = Component.Color.<Vector4f>for_entity(ecs, sun_light_entity);
        var sun_direction  = Component.Direction.<Vector3f>for_entity(ecs, sun_light_entity);
        var sun_intensity  = Component.LightIntensity.<LightIntensity>for_entity(ecs, sun_light_entity);

        var moon_color     = Component.Color.<Vector4f>for_entity(ecs, moon_light_entity);
        var moon_direction = Component.Direction.<Vector3f>for_entity(ecs, moon_light_entity);
        var moon_intensity = Component.LightIntensity.<LightIntensity>for_entity(ecs, moon_light_entity);

        var tod            = Component.TimeOfDay.<MutableFloat>global(ecs);
        var point_lights   = Component.PointLightCount.<MutableInt>global(ecs);
        var spot_lights    = Component.SpotLightCount.<MutableInt>global(ecs);

        DirectionalLight.map_at_offset(light_segment, offset + sun_offset, sun_color, sun_direction, sun_intensity);
        DirectionalLight.map_at_offset(light_segment, offset + moon_offset, moon_color, moon_direction, moon_intensity);

        blend.set(light_segment, offset, tod.value);
        point_light_count.set(light_segment, offset, point_lights.value);
        spot_light_count.set(light_segment, offset, spot_lights.value);
    }
}
