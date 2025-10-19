package com.controllerface.trongle.systems.camera;

import com.juncture.alloy.camera.WorldCamera;
import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.ecs.ECSSystem;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.GPUResourceGroup;
import com.controllerface.trongle.components.Component;
import com.controllerface.trongle.struct.UniformViewData;
import com.controllerface.trongle.systems.rendering.GeometryRenderer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

public class UniformViewSystem extends ECSSystem<Component>
{
    private final WorldCamera camera;

    private final ByteBuffer ubo_view_data;
    private final Arena memory_arena = Arena.ofConfined();
    private final MemorySegment view_segment;

    private final GPUResourceGroup resources = new GPUResourceGroup();

    public UniformViewSystem(ECS<Component> ecs)
    {
        super(ecs);
        camera = Component.MainCamera.global(ecs);
        var ubo = GPU.GL.uniform_buffer(resources, UniformViewData.LAYOUT.byteSize());
        ubo.bind(GeometryRenderer.UBO_BindPoint.VIEW_DATA.ordinal());
        ubo_view_data = ubo.map_as_byte_buffer();
        view_segment = memory_arena.allocate(UniformViewData.LAYOUT);
    }

    @Override
    public void shutdown()
    {
        resources.release_all();
        memory_arena.close();
    }

    @Override
    public void tick(double dt)
    {
        ubo_view_data.clear();
        UniformViewData.map_at_index(view_segment, 0, camera);
        ubo_view_data.put(view_segment.asByteBuffer());
    }
}
