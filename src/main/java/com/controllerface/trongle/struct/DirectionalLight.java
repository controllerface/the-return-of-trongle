package com.controllerface.trongle.struct;

import com.juncture.alloy.data.LightIntensity;
import com.juncture.alloy.utils.memory.glsl.Vec4;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;

import static com.juncture.alloy.utils.memory.Structs.offset;
import static java.lang.foreign.MemoryLayout.structLayout;

/// GLSL directional light struct
///
/// see: [DirectionalLight.glsl](src/main/resources/shaders/_struct/DirectionalLight.glsl)
///
public class DirectionalLight
{
    private static final String LIGHT = "light";
    private static final String DIR   = "direction";

    public static final MemoryLayout LAYOUT = structLayout(
        Light.LAYOUT.withName(LIGHT),
        Vec4.LAYOUT.withName(DIR));

    private static final long light_offset = offset(LAYOUT, LIGHT);
    private static final long dir_offset   = offset(LAYOUT, DIR);

    public static void map_at_offset(MemorySegment segment, long offset, Vector4f color, Vector3f direction, LightIntensity intensity)
    {
        assert color != null;
        assert direction != null;
        assert intensity != null;

        Light.map_at_offset(segment, offset + light_offset, color, intensity);
        Vec4.map_at_offset(segment, offset + dir_offset, direction);
    }
}
