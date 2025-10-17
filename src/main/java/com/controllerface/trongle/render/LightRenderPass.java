package com.controllerface.trongle.render;

import com.controllerface.trongle.Component;
import com.controllerface.trongle.struct.PointLight;
import com.controllerface.trongle.struct.SpotLight;
import com.juncture.alloy.data.LightEmitterType;
import com.juncture.alloy.data.MutableInt;
import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.gl.buffers.GL_ShaderStorageBuffer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class LightRenderPass extends RenderPass
{
    private GL_ShaderStorageBuffer point_light_ssbo;
    private GL_ShaderStorageBuffer spot_light_ssbo;

    private ByteBuffer point_light_buffer;
    private ByteBuffer spot_light_buffer;

    private final List<String> point_light_entities = new ArrayList<>();
    private final List<String> spot_light_entities = new ArrayList<>();

    private final MutableInt point_light_count;
    private final MutableInt spot_light_count;

    private int max_point_lights;
    private int max_spot_lights;

    private final ECS<Component> ecs;

    private Arena memory_arena = Arena.ofConfined();
    private MemorySegment point_segment;
    private MemorySegment spot_segment;

    public LightRenderPass(ECS<Component> ecs)
    {
        this.ecs = ecs;

        this.point_light_count = Component.PointLightCount.global(this.ecs);
        this.spot_light_count = Component.SpotLightCount.global(this.ecs);
    }

    private void resize_off_heap_buffers()
    {
        // if any segments must be resized, all of them must be recreated from a new arena
        if (point_light_count.value > max_point_lights
            || spot_light_count.value > max_spot_lights)
        {
            if (memory_arena != null)
            {
                memory_arena.close();
            }
            memory_arena = Arena.ofConfined();
            point_segment = memory_arena.allocate(PointLight.LAYOUT, point_light_count.value);
            spot_segment = memory_arena.allocate(SpotLight.LAYOUT, spot_light_count.value);
        }
    }

    private void resize_buffers()
    {
        if (point_light_count.value > max_point_lights)
        {
            if (point_light_ssbo != null)
            {
                point_light_ssbo.unmap_buffer();
                resources.release(point_light_ssbo);
            }
            point_light_ssbo = GPU.GL.shader_storage_buffer(resources, PointLight.LAYOUT.byteSize() * point_light_count.value);
            point_light_buffer = point_light_ssbo.map_as_byte_buffer();
            point_light_ssbo.bind(GeometryRenderer.SSBO_BindPoint.POINT_LIGHT.ordinal());
            max_point_lights = point_light_count.value;
        }

        if (spot_light_count.value > max_spot_lights)
        {
            if (spot_light_ssbo != null)
            {
                spot_light_ssbo.unmap_buffer();
                resources.release(spot_light_ssbo);
            }
            spot_light_ssbo = GPU.GL.shader_storage_buffer(resources, SpotLight.LAYOUT.byteSize() * spot_light_count.value);
            spot_light_buffer = spot_light_ssbo.map_as_byte_buffer();
            spot_light_ssbo.bind(GeometryRenderer.SSBO_BindPoint.SPOT_LIGHT.ordinal());
            max_spot_lights = spot_light_count.value;
        }
    }

    private void update_lighting()
    {
        point_light_entities.clear();
        spot_light_entities.clear();

        var lights = ecs.get_components(Component.Light);
        if (lights.isEmpty())
        {
            return;
        }

        lights.forEach((entity, light) ->
        {
            if (light instanceof LightEmitterType emitter)
            {
                switch (emitter)
                {
                    case POINT -> point_light_entities.add(entity);
                    case SPOT -> spot_light_entities.add(entity);
                    case DIRECTIONAL ->
                    {
                    }
                }
            }
        });

        point_light_count.value = point_light_entities.size();
        spot_light_count.value = spot_light_entities.size();

        resize_off_heap_buffers();
        resize_buffers();

        point_light_buffer.clear();
        spot_light_buffer.clear();

        if (!point_light_entities.isEmpty())
        {
            int light_index = 0;
            for (var light : point_light_entities)
            {
                PointLight.ecs_map_at_index(point_segment, light_index++, ecs, light);
            }
            point_light_buffer.put(0, point_segment.asByteBuffer(), 0, (int) PointLight.LAYOUT.byteSize() * point_light_count.value);
        }

        if (!spot_light_entities.isEmpty())
        {
            int light_index = 0;
            for (var light : spot_light_entities)
            {
                SpotLight.ecs_map_at_index(spot_segment, light_index++, ecs, light);
            }
            spot_light_buffer.put(0, spot_segment.asByteBuffer(), 0, (int) SpotLight.LAYOUT.byteSize() * spot_light_count.value);
        }
    }

    @Override
    public void release()
    {
        super.release();
        memory_arena.close();
    }

    @Override
    public void render()
    {
        update_lighting();
    }
}
