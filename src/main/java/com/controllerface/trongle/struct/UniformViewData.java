package com.controllerface.trongle.struct;

import com.juncture.alloy.camera.WorldCamera;
import com.juncture.alloy.utils.memory.glsl.Mat4;
import com.juncture.alloy.utils.memory.glsl.Vec3;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;

import static com.juncture.alloy.utils.memory.Structs.offset;
import static com.juncture.alloy.utils.memory.Structs.pad_float;
import static java.lang.foreign.MemoryLayout.structLayout;

///
/// GLSL Uniform data block for camera matrices
///
/// see: [ViewData.glsl](src/main/resources/shaders/_layout/ViewData.glsl)
///
public class UniformViewData
{
    private static final String VIEW      = "view";
    private static final String PROJ      = "projection";
    private static final String SCR_PROJ  = "screenProjection";
    private static final String VIEW_PROJ = "viewProjection";
    private static final String SKY_VIEW  = "skyboxView";
    private static final String VIEW_POS  = "viewPosition";

    public static MemoryLayout LAYOUT = structLayout(
        Mat4.LAYOUT.withName(VIEW),
        Mat4.LAYOUT.withName(PROJ),
        Mat4.LAYOUT.withName(SCR_PROJ),
        Mat4.LAYOUT.withName(VIEW_PROJ),
        Mat4.LAYOUT.withName(SKY_VIEW),
        Vec3.LAYOUT.withName(VIEW_POS),
        pad_float(1));

    private static final long view_offset        = offset(LAYOUT, VIEW);
    private static final long proj_offset        = offset(LAYOUT, PROJ);
    private static final long screen_proj_offset = offset(LAYOUT, SCR_PROJ);
    private static final long view_proj_offset   = offset(LAYOUT, VIEW_PROJ);
    private static final long sky_view_offset    = offset(LAYOUT, SKY_VIEW);
    private static final long view_pos_offset    = offset(LAYOUT, VIEW_POS);

    public static void map_at_index(MemorySegment segment, int index, WorldCamera camera)
    {
        assert camera != null;

        long offset = index * LAYOUT.byteSize();

        Mat4.map_at_offset(segment, offset + view_offset, camera.view_matrix());
        Mat4.map_at_offset(segment, offset + proj_offset, camera.projection_matrix());
        Mat4.map_at_offset(segment, offset + screen_proj_offset, camera.screen_matrix());
        Mat4.map_at_offset(segment, offset + view_proj_offset, camera.uvp());
        Mat4.map_at_offset(segment, offset + sky_view_offset, camera.sky_view_matrix());
        Vec3.map_at_offset(segment, offset + view_pos_offset, camera.position());
    }
}
