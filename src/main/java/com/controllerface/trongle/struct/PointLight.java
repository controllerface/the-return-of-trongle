package com.controllerface.trongle.struct;

import com.juncture.alloy.data.LightEmitterType;
import com.juncture.alloy.data.LightIntensity;
import com.juncture.alloy.data.MutableFloat;
import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.utils.memory.glsl.Vec4;
import com.controllerface.trongle.components.Component;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

import static com.juncture.alloy.utils.memory.Structs.*;
import static java.lang.foreign.MemoryLayout.structLayout;

/// GLSL point light struct
///
/// see: [PointLight.glsl](src/main/resources/shaders/_struct/PointLight.glsl)
///
public class PointLight
{
    private static final String LIGHT = "light";
    private static final String POS   = "position";
    private static final String RANGE = "range";

    public static final MemoryLayout LAYOUT = structLayout(
        Light.LAYOUT.withName(LIGHT),
        Vec4.LAYOUT.withName(POS),
        of_float(RANGE),
        pad_float(3));

    private static final long light_offset = offset(LAYOUT, LIGHT);
    private static final long pos_offset   = offset(LAYOUT, POS);

    private static final VarHandle _range = var_handle(LAYOUT, RANGE);

    public static void ecs_map_at_index(MemorySegment light_segment, int index, ECSLayer<Component> ecs, String entity)
    {
        var emitter = Component.Light.<LightEmitterType>for_entity(ecs, entity);
        assert emitter == LightEmitterType.POINT;

        var color     = Component.Color.<Vector4f>for_entity(ecs, entity);
        var position  = Component.RenderPosition.<Vector3f>for_entity(ecs, entity);
        var intensity = Component.LightIntensity.<LightIntensity>for_entity(ecs, entity);
        var range     = Component.LightRange.<MutableFloat>for_entity(ecs, entity);

        long offset = index * LAYOUT.byteSize();

        map_at_offset(light_segment, offset, color, position, intensity, range);
    }

    public static void map_at_offset(MemorySegment light_segment,
                                     long offset,
                                     Vector4f color,
                                     Vector3f position,
                                     LightIntensity intensity,
                                     MutableFloat range)
    {
        assert color != null;
        assert position != null;
        assert intensity != null;

        Light.map_at_offset(light_segment, offset + light_offset, color, intensity);
        Vec4.map_at_offset(light_segment, offset + pos_offset, position);
        _range.set(light_segment, offset, range.value);
    }
}
