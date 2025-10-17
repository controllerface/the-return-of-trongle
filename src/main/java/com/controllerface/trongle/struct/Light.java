package com.controllerface.trongle.struct;

import com.juncture.alloy.data.LightIntensity;
import com.juncture.alloy.utils.memory.glsl.Vec4;
import org.joml.Vector4f;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

import static com.juncture.alloy.utils.memory.Structs.*;
import static java.lang.foreign.MemoryLayout.structLayout;

/// GLSL base light struct
///
/// see: [Light.glsl](src/main/resources/shaders/_struct/Light.glsl)
///
public class Light
{
    private static final String COLOR   = "color";
    private static final String D_INTEN = "dIntensity";
    private static final String A_INTEN = "aIntensity";

    public static MemoryLayout LAYOUT = structLayout(
        Vec4.LAYOUT.withName(COLOR),
        of_float(D_INTEN),
        of_float(A_INTEN),
        pad_float(2));

    private static final long color_offset = offset(LAYOUT, COLOR);

    private static final VarHandle dIntensity = var_handle(LAYOUT, D_INTEN);
    private static final VarHandle aIntensity = var_handle(LAYOUT, A_INTEN);

    public static void map_at_offset(MemorySegment segment, long offset, Vector4f color, LightIntensity intensity)
    {
        assert color != null;
        assert intensity != null;

        Vec4.map_at_offset(segment ,offset + color_offset, color);

        dIntensity.set(segment, offset, intensity.diffuse);
        aIntensity.set(segment, offset, intensity.ambient);
    }
}
