package com.controllerface.trongle.systems.rendering.passes.base;

import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.RenderSet;
import com.juncture.alloy.gpu.Window;
import com.juncture.alloy.gpu.gl.buffers.*;
import com.juncture.alloy.gpu.gl.shaders.GL_Shader;
import com.juncture.alloy.gpu.gl.textures.GL_CubeMap;
import com.juncture.alloy.gpu.gl.textures.GL_ShadowTexture;
import com.juncture.alloy.gpu.gl.textures.GL_TextureArray;
import com.juncture.alloy.models.ModelAsset;
import com.juncture.alloy.models.ModelRegistry;
import com.juncture.alloy.models.data.MaterialTextures;
import com.juncture.alloy.models.data.ModelBuffers;
import com.juncture.alloy.models.data.ModelMetaData;
import com.juncture.alloy.rendering.RenderComponent;
import com.juncture.alloy.utils.memory.glsl.Mat4;
import com.juncture.alloy.utils.memory.opengl.DrawElementsIndirectCommand;
import com.controllerface.trongle.main.GLTFModel;
import org.joml.Matrix4f;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

import static com.juncture.alloy.gpu.Constants.*;
import static com.controllerface.trongle.systems.rendering.GeometryRenderer.SSBO_BindPoint.MESH_TRANSFORM;
import static com.controllerface.trongle.systems.rendering.GeometryRenderer.SSBO_BindPoint.MODEL_TRANSFORM;
import static com.controllerface.trongle.systems.rendering.GeometryRenderer.Texture_BindPoint.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL43C.glMultiDrawElementsIndirect;
import static org.lwjgl.opengl.GL45C.glVertexArrayBindingDivisor;

public class ModelRenderPass extends RenderPass
{
    private static final int INITIAL_INSTANCE_COUNT = 0;

    private static final int POS_ATTRIBUTE       = 0;
    private static final int UV_ATTRIBUTE        = 1;
    private static final int NORMAL_ATTRIBUTE    = 2;
    private static final int TANGENT_ATTRIBUTE   = 3;
    private static final int BITANGENT_ATTRIBUTE = 4;
    private static final int COLOR_ATTRIBUTE     = 5;
    private static final int MATERIAL_ATTRIBUTE  = 6;
    private static final int MODEL_ID_ATTRIBUTE  = 7;

    private final ECSLayer<RenderComponent> recs;

    private final Window window;
    private final Matrix4f light_space_matrix;

    private final GL_VertexArray vao;
    private final GL_CommandBuffer cbo;

    private final ByteBuffer command_buffer;

    private final GL_CubeMap skybox_texture;
    private final GL_CubeMap skybox_night_texture;

    private final GL_TextureArray diffuse_maps;
    private final GL_TextureArray surface_maps;
    private final GL_TextureArray normal_maps;
    private final GL_Shader shader;
    private final GL_Shader shadow_shader;
    private final GL_ShadowTexture shadow_texture;
    private final GL_FrameBuffer shadow_framebuffer;

    private final ModelMetaData<GLTFModel> model_data = new ModelMetaData<>(GLTFModel.class);

    private final Map<GLTFModel, List<String>> model_instance_buffer = new EnumMap<>(GLTFModel.class);
    private final Set<GLTFModel> model_type_buffer = EnumSet.noneOf(GLTFModel.class);

    private int max_mesh_count = 0;
    private int mesh_count = INITIAL_INSTANCE_COUNT;
    private int max_model_count = 0;
    private int model_count = INITIAL_INSTANCE_COUNT;
    private int command_object_count = 0;
    private int max_command_object_count = INITIAL_INSTANCE_COUNT;

    private GL_ShaderStorageBuffer mesh_transform_ssbo;
    private GL_ShaderStorageBuffer model_transform_ssbo;
    private GL_VertexBuffer material_id_vbo;
    private GL_VertexBuffer transform_id_vbo;

    private ByteBuffer mesh_transform_buffer;
    private ByteBuffer model_transform_buffer;
    private FloatBuffer material_id_buffer;
    private IntBuffer transform_id_buffer;

    private Arena memory_arena = Arena.ofConfined();
    private MemorySegment command_segment;
    private MemorySegment model_matrix_segment;
    private MemorySegment mesh_matrix_segment;

    public ModelRenderPass(ECSLayer<RenderComponent> recs,
                           GL_CubeMap skybox_texture,
                           GL_CubeMap skybox_texture_dark,
                           GL_ShadowTexture shadow_texture)
    {
        this.recs = recs;
        this.skybox_texture = skybox_texture;
        this.skybox_night_texture = skybox_texture_dark;
        this.shadow_texture = shadow_texture;

        this.window = RenderComponent.MainWindow.global(recs);
        this.light_space_matrix = RenderComponent.LightSpaceMatrix.global(recs);

        var model_registry = RenderComponent.Models.<ModelRegistry>global(this.recs);
        var model_metrics = model_registry.model_metrics();
        var command_buffer_size = DrawElementsIndirectCommand.calculate_buffer_size(model_metrics.mesh_count());

        shader = GPU.GL.new_shader(resources, "model");
        vao = GPU.GL.new_vao(resources);
        cbo = GPU.GL.command_buffer(resources, command_buffer_size);

        var ebo = GPU.GL.element_buffer(resources, vao, model_metrics.face_size());
        var uv_vbo = GPU.GL.vec2_buffer(resources, vao, UV_ATTRIBUTE, model_metrics.uv_size());
        var pos_vbo = GPU.GL.vec3_buffer(resources, vao, POS_ATTRIBUTE, model_metrics.vertex_size());
        var nrm_vbo = GPU.GL.vec3_buffer(resources, vao, NORMAL_ATTRIBUTE, model_metrics.vertex_size());
        var tan_vbo = GPU.GL.vec3_buffer(resources, vao, TANGENT_ATTRIBUTE, model_metrics.vertex_size());
        var btn_vbo = GPU.GL.vec3_buffer(resources, vao, BITANGENT_ATTRIBUTE, model_metrics.vertex_size());
        var clr_vbo = GPU.GL.vec4_buffer(resources, vao, COLOR_ATTRIBUTE, model_metrics.color_size());
        // todo: probably remove the color attribute, unless some way to use it that doesn't look terrible can be found.

        command_buffer = cbo.map_as_byte_buffer_persistent();

        var model_buffers = new ModelBuffers(
            pos_vbo.map_as_float_buffer(),
            nrm_vbo.map_as_float_buffer(),
            tan_vbo.map_as_float_buffer(),
            btn_vbo.map_as_float_buffer(),
            uv_vbo.map_as_float_buffer(),
            clr_vbo.map_as_float_buffer(),
            ebo.map_as_int_buffer());

        var materials = new MaterialTextures();

        model_registry.load_model_data(GLTFModel.class, model_buffers, model_data, materials);

        pos_vbo.unmap_buffer();
        nrm_vbo.unmap_buffer();
        tan_vbo.unmap_buffer();
        btn_vbo.unmap_buffer();
        clr_vbo.unmap_buffer();
        uv_vbo.unmap_buffer();
        ebo.unmap_buffer();

        diffuse_maps = GPU.GL.new_array_texture(resources, true, "diffuse", materials.diffuse_textures());
        surface_maps = GPU.GL.new_array_texture(resources, false, "surface", materials.surface_textures());
        normal_maps = GPU.GL.new_array_texture(resources, false, "normals", materials.normal_textures());

        resize_transform_buffers();

        vao.enable_attribute(POS_ATTRIBUTE);
        vao.enable_attribute(UV_ATTRIBUTE);
        vao.enable_attribute(NORMAL_ATTRIBUTE);
        vao.enable_attribute(TANGENT_ATTRIBUTE);
        vao.enable_attribute(BITANGENT_ATTRIBUTE);
        vao.enable_attribute(COLOR_ATTRIBUTE);

        shader.use();
        shader.uploadInt(DIFFUSE_MAP.varName, DIFFUSE_MAP.ordinal());
        shader.uploadInt(SURFACE_MAP.varName, SURFACE_MAP.ordinal());
        shader.uploadInt(NORMAL_MAP.varName, NORMAL_MAP.ordinal());
        shader.uploadInt(SKYBOX_DAY_TEXTURE.varName, SKYBOX_DAY_TEXTURE.ordinal());
        shader.uploadInt(SKYBOX_NIGHT_TEXTURE.varName, SKYBOX_NIGHT_TEXTURE.ordinal());
        shader.uploadInt(SHADOW_MAP.varName, SHADOW_MAP.ordinal());
        shader.detach();

        shadow_framebuffer = GPU.GL.new_framebuffer(resources);
        shadow_framebuffer.attach_texture(shadow_texture);
        shadow_shader = GPU.GL.new_shader(resources, "shadow_map");

        shadow_shader.use();
        shadow_shader.uploadInt(SHADOW_MAP.varName, SHADOW_MAP.ordinal());
        shadow_shader.detach();
    }

    private void resize_off_heap_buffers()
    {
        // if any segments must be resized, all of them must be recreated from a new arena
        if (command_object_count > max_command_object_count
            || mesh_count > max_mesh_count
            || model_count > max_model_count)
        {
            max_command_object_count = command_object_count;
            if (memory_arena != null) memory_arena.close();
            memory_arena = Arena.ofConfined();
            command_segment = memory_arena.allocate(DrawElementsIndirectCommand.LAYOUT, command_object_count);
            mesh_matrix_segment = memory_arena.allocate(Mat4.LAYOUT, mesh_count);
            model_matrix_segment = memory_arena.allocate(Mat4.LAYOUT, model_count);
        }
    }

    private void resize_transform_buffers()
    {
        if (mesh_count > max_mesh_count)
        {
            max_mesh_count = mesh_count;
            if (mesh_transform_ssbo != null)
            {
                mesh_transform_ssbo.unmap_buffer();
                resources.release(mesh_transform_ssbo);
            }
            if (material_id_vbo != null)
            {
                material_id_vbo.unmap_buffer();
                resources.release(material_id_vbo);
            }
            if (transform_id_vbo != null)
            {
                transform_id_vbo.unmap_buffer();
                resources.release(transform_id_vbo);
            }

            mesh_transform_ssbo = GPU.GL.shader_storage_buffer(resources, (long) MATRIX_FLOAT_4_SIZE * max_mesh_count);
            material_id_vbo = GPU.GL.float_buffer(resources, vao, MATERIAL_ATTRIBUTE, SCALAR_FLOAT_SIZE * max_mesh_count);
            transform_id_vbo = GPU.GL.ivec2_buffer(resources, vao, MODEL_ID_ATTRIBUTE, (long) VECTOR_INT_2D_SIZE * max_mesh_count);

            mesh_transform_buffer = mesh_transform_ssbo.map_as_byte_buffer();
            material_id_buffer = material_id_vbo.map_as_float_buffer_persistent();
            transform_id_buffer = transform_id_vbo.map_as_int_buffer_persistent();

            mesh_transform_ssbo.bind(MESH_TRANSFORM.ordinal());
            glVertexArrayBindingDivisor(vao.id(), MATERIAL_ATTRIBUTE, 1);
            glVertexArrayBindingDivisor(vao.id(), MODEL_ID_ATTRIBUTE, 1);
            vao.enable_attribute(MATERIAL_ATTRIBUTE);
            vao.enable_attribute(MODEL_ID_ATTRIBUTE);
        }

        if (model_count > max_model_count)
        {
            max_model_count = model_count;
            if (model_transform_ssbo != null)
            {
                model_transform_ssbo.unmap_buffer();
                resources.release(model_transform_ssbo);
            }

            var size = MATRIX_FLOAT_4_SIZE * max_model_count;
            model_transform_ssbo = GPU.GL.shader_storage_buffer(resources, size);
            model_transform_ssbo.bind(MODEL_TRANSFORM.ordinal());
            model_transform_buffer = model_transform_ssbo.map_as_byte_buffer();
        }
    }

    private void update_model_data()
    {
        model_type_buffer.clear();

        mesh_count = 0;
        model_count = 0;
        command_object_count = 0;

        model_instance_buffer.clear();

        var visible_bounds = RenderComponent.RenderVisible.<RenderSet>global(recs);
        for (var bounds : visible_bounds)
        {
            var model = RenderComponent.Model.<GLTFModel>for_entity(recs, bounds.entity);
            var mesh_count = model_data.face_offsets().get(model).length;
            model_instance_buffer
                .computeIfAbsent(model, _ -> new ArrayList<>())
                .add(bounds.entity);
            this.mesh_count += mesh_count;
            model_count++;
            if (model_type_buffer.add(model))
            {
                command_object_count += mesh_count;
            }
        }

        if (command_object_count == 0)
        {
            return;
        }

        resize_off_heap_buffers();
        resize_transform_buffers();

        mesh_transform_buffer.clear();
        model_transform_buffer.clear();
        material_id_buffer.clear();
        command_buffer.clear();
        transform_id_buffer.clear();

        long command_index                 = 0;
        int current_mesh_transform_offset  = 0;
        int current_model_transform_offset = 0;
        int current_mesh_offset            = 0;
        int model_matrix_index             = 0;
        int mesh_matrix_index              = 0;

        for (var entry : model_instance_buffer.entrySet())
        {
            var model = entry.getKey();
            var entities = entry.getValue();

            if (entities.isEmpty()) continue;

            var model_count = entities.size();

            var mesh_count = model_data.face_offsets().get(model).length;

            var mesh_offsets = new int[mesh_count];
            for (var i = 0; i < mesh_count; i++)
            {
                DrawElementsIndirectCommand.map_at_index(command_segment,
                    command_index++,
                    model_data.face_counts().get(model)[i],
                    model_count,
                    model_data.face_offsets().get(model)[i],
                    model_data.vertex_offsets().get(model)[i],
                    current_mesh_offset);

                var material_ids = new float[model_count];
                var material_id = model_data.mesh_material_ids().get(model)[i];
                Arrays.fill(material_ids, model_data.model_material_ids().get(model)[material_id]);
                material_id_buffer.put(material_ids);
                mesh_offsets[i] = current_mesh_offset;
                current_mesh_offset += model_count;
            }

            for (var model_instance_id = 0; model_instance_id < entities.size(); model_instance_id++)
            {
                var entity = entities.get(model_instance_id);
                var transform = RenderComponent.Transform.<Matrix4f>for_entity(recs, entity);
                Mat4.map_at_index(model_matrix_segment, model_matrix_index++, transform);

                for (var mesh_index = 0; mesh_index < mesh_offsets.length; mesh_index++)
                {
                    Mat4.map_at_index(mesh_matrix_segment, mesh_matrix_index++, model_data.mesh_transforms().get(model)[mesh_index]);
                    var index = (mesh_offsets[mesh_index] + model_instance_id) * 2;
                    transform_id_buffer.put(index, new int[]{current_mesh_transform_offset, current_model_transform_offset});
                    current_mesh_transform_offset++;
                }
                current_model_transform_offset++;
            }

            command_buffer.put(0, command_segment.asByteBuffer(), 0, (int) DrawElementsIndirectCommand.LAYOUT.byteSize() * this.command_object_count);
            model_transform_buffer.put(0, model_matrix_segment.asByteBuffer(), 0, (int) Mat4.LAYOUT.byteSize() * this.model_count);
            mesh_transform_buffer.put(0, mesh_matrix_segment.asByteBuffer(), 0, (int) Mat4.LAYOUT.byteSize() * this.mesh_count);
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
        update_model_data();

        if (command_object_count == 0)
        {
            return;
        }

        vao.bind();
        cbo.bind();

        // shadow pass
        glViewport(0, 0, GL_ShadowTexture.SHADOW_MAP_RESOLUTION, GL_ShadowTexture.SHADOW_MAP_RESOLUTION);
        glScissor(0, 0, GL_ShadowTexture.SHADOW_MAP_RESOLUTION, GL_ShadowTexture.SHADOW_MAP_RESOLUTION);
        shadow_framebuffer.bind();
        glClear(GL_DEPTH_BUFFER_BIT);
        shadow_shader.use();
        shadow_shader.uploadMat4f("lightSpaceMatrix", light_space_matrix);
        shadow_texture.bind(SHADOW_MAP.ordinal());
        glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, 0, command_object_count, 0);
        shadow_framebuffer.unbind();
        shadow_shader.detach();

        // normal pass
        glViewport(0, 0, window.width(), window.height());
        glScissor(0, 0, window.width(), window.height());
        shader.use();
        shader.uploadMat4f("lightSpaceMatrix", light_space_matrix);
        diffuse_maps.bind(DIFFUSE_MAP.ordinal());
        surface_maps.bind(SURFACE_MAP.ordinal());
        normal_maps.bind(NORMAL_MAP.ordinal());
        skybox_texture.bind(SKYBOX_DAY_TEXTURE.ordinal());
        skybox_night_texture.bind(SKYBOX_NIGHT_TEXTURE.ordinal());
        shadow_texture.bind(SHADOW_MAP.ordinal());
        glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, 0, command_object_count, 0);
        shader.detach();
        vao.unbind();
    }
}
