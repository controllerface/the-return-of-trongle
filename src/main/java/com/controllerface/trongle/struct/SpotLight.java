package com.controllerface.trongle.struct;

import com.controllerface.trongle.Component;
import com.juncture.alloy.data.LightEmitterType;
import com.juncture.alloy.data.LightIntensity;
import com.juncture.alloy.data.MutableFloat;
import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.utils.memory.glsl.Vec4;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

import static com.juncture.alloy.utils.memory.Structs.*;
import static java.lang.foreign.MemoryLayout.structLayout;

/// GLSL spotlight struct
///
/// see: [SpotLight.glsl](src/main/resources/shaders/_struct/SpotLight.glsl)
///
public class SpotLight
{
    private static final String POINT    = "point";
    private static final String DIR      = "direction";
    private static final String IN_CONE  = "innerCone";
    private static final String OUT_CONE = "outerCone";

    public static final MemoryLayout LAYOUT = structLayout(
        PointLight.LAYOUT.withName(POINT),
        Vec4.LAYOUT.withName(DIR),
        of_float(IN_CONE),
        of_float(OUT_CONE),
        pad_float(2));

    private static final long point_light_offset = offset(LAYOUT, POINT);
    private static final long dir_offset         = offset(LAYOUT, DIR);

    private static final VarHandle inner_cone = var_handle(LAYOUT, IN_CONE);
    private static final VarHandle outer_cone = var_handle(LAYOUT, OUT_CONE);

    public static void ecs_map_at_index(MemorySegment light_segment, int index, ECS<Component> ecs, String entity)
    {
        var emitter   = Component.Light.<LightEmitterType>for_entity(ecs, entity);
        var color     = Component.Color.<Vector4f>for_entity(ecs, entity);
        var position  = Component.RenderPosition.<Vector3f>for_entity(ecs, entity);
        var direction = Component.Direction.<Vector3f>for_entity(ecs, entity);
        var intensity = Component.LightIntensity.<LightIntensity>for_entity(ecs, entity);
        var range     = Component.LightRange.<MutableFloat>for_entity(ecs, entity);
        var inner     = Component.InnerCone.<MutableFloat>for_entity(ecs, entity);
        var outer     = Component.OuterCone.<MutableFloat>for_entity(ecs, entity);

        assert emitter == LightEmitterType.SPOT;

        long offset = index * LAYOUT.byteSize();

        PointLight.map_at_offset(light_segment, offset + point_light_offset, color, position, intensity, range);
        Vec4.map_at_offset(light_segment, offset + dir_offset, direction);

        inner_cone.set(light_segment, offset, inner.value);
        outer_cone.set(light_segment, offset, outer.value);
    }
}
