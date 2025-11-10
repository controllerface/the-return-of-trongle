package com.controllerface.trongle.systems.rendering.passes.base;

import com.juncture.alloy.data.LightEmitterType;
import com.juncture.alloy.data.MutableFloat;
import com.juncture.alloy.data.MutableInt;
import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.gl.buffers.GL_ShaderStorageBuffer;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexArray;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexBuffer;
import com.juncture.alloy.gpu.gl.shaders.GL_Shader;
import com.juncture.alloy.utils.memory.glsl.Vec3;
import com.juncture.alloy.utils.memory.glsl.Vec4;
import com.controllerface.trongle.components.Component;
import com.controllerface.trongle.struct.PointLight;
import com.controllerface.trongle.struct.SpotLight;
import com.controllerface.trongle.systems.rendering.GeometryRenderer;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.juncture.alloy.gpu.Constants.VECTOR_FLOAT_3D_SIZE;
import static com.juncture.alloy.gpu.Constants.VECTOR_FLOAT_4D_SIZE;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL31C.glDrawElementsInstanced;

public class LightRenderPass extends RenderPass
{
    private static final boolean DEBUG = false;

    private static final int DBG_POS_ATTRIBUTE = 0;
    private static final int DBG_TRANSFORM_ATTRIBUTE = 1;
    private static final int DBG_COLOR_ATTRIBUTE = 2;

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

    private final ECSLayer<Component> ecs;

    private GL_Shader dbg_shader;
    private GL_VertexArray dbg_vao;
    private GL_VertexBuffer dbg_transform_vbo;
    private GL_VertexBuffer dbg_color_vbo;
    private ByteBuffer dbg_transform_buffer;
    private ByteBuffer dbg_color_buffer;
    private int dbg_index_count = 0;
    private int dbg_light_count = 0;
    private int dbg_max_light_count = 0;

    private Arena memory_arena = Arena.ofConfined();
    private MemorySegment point_segment;
    private MemorySegment spot_segment;

    public LightRenderPass(ECSLayer<Component> ecs)
    {
        this.ecs = ecs;

        this.point_light_count = Component.PointLightCount.global(this.ecs);
        this.spot_light_count = Component.SpotLightCount.global(this.ecs);

        if (DEBUG)
        {
            debug_setup();
        }
    }

    private void debug_setup()
    {
        var sphere = new SphereMesh(12, 12);

        dbg_index_count = sphere.indices.length;
        dbg_shader = GPU.GL.new_shader(resources, "light_radius");
        dbg_vao = GPU.GL.new_vao(resources);
        GPU.GL.vec3_buffer_static(resources, dbg_vao, DBG_POS_ATTRIBUTE, sphere.vertices);
        GPU.GL.element_buffer_static(resources, dbg_vao, sphere.indices);
        dbg_vao.enable_attribute(DBG_POS_ATTRIBUTE);
    }

    private void update_debug_data()
    {
        if (dbg_light_count > dbg_max_light_count)
        {
            if (dbg_transform_vbo != null)
            {
                dbg_transform_vbo.unmap_buffer();
                resources.release(dbg_transform_vbo);
            }
            if (dbg_color_vbo != null)
            {
                dbg_color_vbo.unmap_buffer();
                resources.release(dbg_color_vbo);
            }

            dbg_transform_vbo = GPU.GL.vec4_buffer(resources, dbg_vao, DBG_TRANSFORM_ATTRIBUTE, dbg_light_count * VECTOR_FLOAT_4D_SIZE);
            dbg_color_vbo = GPU.GL.vec3_buffer(resources, dbg_vao, DBG_COLOR_ATTRIBUTE, dbg_light_count * VECTOR_FLOAT_3D_SIZE);

            dbg_transform_buffer = dbg_transform_vbo.map_as_byte_buffer_persistent();
            dbg_color_buffer = dbg_color_vbo.map_as_byte_buffer_persistent();

            dbg_vao.instance_attribute(DBG_TRANSFORM_ATTRIBUTE, 1);
            dbg_vao.instance_attribute(DBG_COLOR_ATTRIBUTE, 1);

            dbg_vao.enable_attribute(DBG_TRANSFORM_ATTRIBUTE);
            dbg_vao.enable_attribute(DBG_COLOR_ATTRIBUTE);

            dbg_max_light_count = dbg_light_count;
        }

        try (var arena = Arena.ofConfined())
        {
            dbg_transform_buffer.clear();
            dbg_color_buffer.clear();
            var transform_segment = arena.allocate(Vec4.LAYOUT, dbg_light_count);
            var color_segment = arena.allocate(Vec3.LAYOUT, dbg_light_count);
            int light_index = 0;
            for (var entity : point_light_entities)
            {
                var color = Component.Color.<Vector4f>for_entity(ecs, entity);
                var position = Component.RenderPosition.<Vector3f>for_entity(ecs, entity);
                var radius = Component.LightRange.<MutableFloat>for_entity(ecs, entity);
                Vec4.map_at_index(transform_segment, light_index, position.x, position.y, position.z, radius.value);
                Vec3.map_at_index(color_segment, light_index, color.x, color.y, color.z);
                light_index++;
            }
            for (var entity : spot_light_entities)
            {
                var color = Component.Color.<Vector4f>for_entity(ecs, entity);
                var position = Component.RenderPosition.<Vector3f>for_entity(ecs, entity);
                var radius = Component.LightRange.<MutableFloat>for_entity(ecs, entity);
                Vec4.map_at_index(transform_segment, light_index, position.x, position.y, position.z, radius.value);
                Vec3.map_at_index(color_segment, light_index, color.x, color.y, color.z);
                light_index++;
            }

            dbg_transform_buffer.put(transform_segment.asByteBuffer());
            dbg_color_buffer.put(color_segment.asByteBuffer());
        }
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

    private static final class SphereMesh
    {
        public float[] vertices;
        public int[] indices;

        public SphereMesh(int latitudeBands, int longitudeBands)
        {
            List<Float> verts = new ArrayList<>();
            List<Integer> inds = new ArrayList<>();

            for (int lat = 0; lat <= latitudeBands; lat++)
            {
                float theta = (float) (lat * Math.PI / latitudeBands);
                float sinTheta = (float) Math.sin(theta);
                float cosTheta = (float) Math.cos(theta);

                for (int lon = 0; lon <= longitudeBands; lon++)
                {
                    float phi = (float) (lon * 2 * Math.PI / longitudeBands);
                    float sinPhi = (float) Math.sin(phi);
                    float cosPhi = (float) Math.cos(phi);

                    float x = cosPhi * sinTheta;
                    float y = cosTheta;
                    float z = sinPhi * sinTheta;

                    verts.add(x);
                    verts.add(y);
                    verts.add(z);
                }
            }

            for (int lat = 0; lat < latitudeBands; lat++)
            {
                for (int lon = 0; lon < longitudeBands; lon++)
                {
                    int first = (lat * (longitudeBands + 1)) + lon;
                    int second = first + longitudeBands + 1;

                    inds.add(first);
                    inds.add(second);
                    inds.add(first + 1);

                    inds.add(second);
                    inds.add(second + 1);
                    inds.add(first + 1);
                }
            }

            // Convert lists to arrays
            this.vertices = new float[verts.size()];
            for (int i = 0; i < verts.size(); i++)
            {
                this.vertices[i] = verts.get(i);
            }

            this.indices = new int[inds.size()];
            for (int i = 0; i < inds.size(); i++)
            {
                this.indices[i] = inds.get(i);
            }
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

        if (DEBUG)
        {
            dbg_light_count = point_light_entities.size() + spot_light_entities.size();
            update_debug_data();
            glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
            dbg_vao.bind();
            dbg_shader.use();
            glDrawElementsInstanced(GL_TRIANGLES, dbg_index_count, GL_UNSIGNED_INT, 0, dbg_light_count);
            dbg_shader.detach();
            dbg_vao.unbind();
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        }
    }
}
