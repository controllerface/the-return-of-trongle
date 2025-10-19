package com.controllerface.trongle.systems.rendering.passes.base;

import com.juncture.alloy.camera.WorldCamera;
import com.juncture.alloy.data.MutableFloat;
import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.gl.buffers.GL_CommandBuffer;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexArray;
import com.juncture.alloy.gpu.gl.buffers.GL_VertexBuffer;
import com.juncture.alloy.gpu.gl.shaders.GL_Shader;
import com.juncture.alloy.gpu.gl.textures.GL_Texture;
import com.juncture.alloy.physics.bvh.RenderNode;
import com.juncture.alloy.physics.bvh.RenderTree;
import com.juncture.alloy.utils.math.Bounds3f;
import com.juncture.alloy.utils.math.MathEX;
import com.juncture.alloy.utils.math.RenderExtents;
import com.juncture.alloy.utils.memory.opengl.DrawElementsIndirectCommand;
import com.juncture.alloy.utils.noise.FastNoiseLite;
import com.controllerface.trongle.components.Component;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static com.juncture.alloy.gpu.Constants.VECTOR_FLOAT_3D_SIZE;
import static org.lwjgl.opengl.GL40C.*;
import static org.lwjgl.opengl.GL43C.glMultiDrawElementsIndirect;

public class TerrainRenderPass extends RenderPass
{
    private static final boolean DEBUG = false;

    private static final int PATCH_INDEX_COUNT    = 4;
    private static final int PATCH_INSTANCE_COUNT = 1;
    private static final int PATCH_BASE_VERTEX    = 0;

    private static final int GRID_SIZE = 128;
    private static final float CELL_SIZE = 512.0f;
    private static final float UV_TILING = 128.0f;
    private static final float FLOOR = -512.0f;
    private static final float BOTTOM_RANGE = -1000.0f;
    private static final float TOP_RANGE = 500.0f;

    private static final float TREE_MIN_NODE_SIZE = CELL_SIZE * 8;

    private static final int XYZ_ATTRIBUTE = 0;
    private static final int NORMAL_ATTRIBUTE = 1;
    private static final int TANGENT_ATTRIBUTE = 2;
    private static final int BITANGENT_ATTRIBUTE = 3;
    private static final int UV_ATTRIBUTE = 4;
    private static final int COLOR_ATTRIBUTE = 5;

    private static final int DBG_MIN_ATTRIBUTE = 0;
    private static final int DBG_MAX_ATTRIBUTE = 1;
    private static final int DBG_COLOR_ATTRIBUTE = 2;

    private final FastNoiseLite noise = new FastNoiseLite();
    private final FastNoiseLite S_noise = new FastNoiseLite(398349);
    private final FastNoiseLite V_noise = new FastNoiseLite(872582);

    private final GL_VertexArray vao;
    private final GL_CommandBuffer cbo;
    private final GL_Shader shader;
    private final GL_Texture terrain_low;
    private final GL_Texture terrain_mid;
    private final GL_Texture terrain_high;
    private final GL_Texture terrain_top;
    private final GL_Texture terrain_low_normal;
    private final GL_Texture terrain_mid_normal;
    private final GL_Texture terrain_high_normal;
    private final GL_Texture terrain_top_normal;
    private final GL_Texture terrain_low_height;
    private final GL_Texture terrain_mid_height;
    private final GL_Texture terrain_high_height;
    private final GL_Texture terrain_top_height;

    private final ByteBuffer command_buffer;

    private final Vector3f edge1_buffer = new Vector3f();
    private final Vector3f edge2_buffer = new Vector3f();
    private final Vector3f normal_buffer = new Vector3f();
    private final Vector3f tangent_buffer = new Vector3f();
    private final Vector3f bitangent_buffer = new Vector3f();
    private final Vector2f uv1_buffer = new Vector2f();
    private final Vector2f uv2_buffer = new Vector2f();

    private final float[] terrain_ranges;

    private int render_patch_count;
    private int max_render_patch_count;

    private final WorldCamera camera;
    private final RenderTree render_tree;

    private GL_Shader dbg_shader;
    private GL_VertexArray dbg_vao;
    private GL_VertexBuffer dbg_min_vbo;
    private GL_VertexBuffer dbg_max_vbo;
    private GL_VertexBuffer dbg_color_vbo;
    private FloatBuffer dbg_min_vbo_buffer;
    private FloatBuffer dbg_max_vbo_buffer;
    private FloatBuffer dbg_color_vbo_buffer;
    private int dbg_box_count = 0;

    private Arena memory_arena = Arena.ofConfined();
    private MemorySegment command_segment;

    private final MutableFloat time_index;

    public TerrainRenderPass(ECS<Component> ecs)
    {
        this.camera = Component.MainCamera.global(ecs);
        this.time_index = Component.TimeIndex.global(ecs);

        terrain_ranges = generate_height_intervals(BOTTOM_RANGE, TOP_RANGE, FLOOR);

        noise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        noise.SetFractalType(FastNoiseLite.FractalType.FBm);

        S_noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        S_noise.SetFractalType(FastNoiseLite.FractalType.FBm);

        V_noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        V_noise.SetFractalType(FastNoiseLite.FractalType.FBm);

        var vertex_buffer = new ArrayList<Vector3f>();
        var normal_buffer = new ArrayList<Vector3f>();
        var tangent_buffer = new ArrayList<Vector3f>();
        var bitangent_buffer = new ArrayList<Vector3f>();
        var uv_buffer = new ArrayList<Vector2f>();
        var index_buffer = new ArrayList<Integer>();
        var color_buffer = new ArrayList<Vector3f>();
        var tree_queue = new ArrayDeque<Bounds3f>();
        var world_extents = new RenderExtents();

        int patch_count = generate_terrain_grid(
            tree_queue,
            world_extents,
            color_buffer,
            vertex_buffer,
            normal_buffer,
            tangent_buffer,
            bitangent_buffer,
            uv_buffer,
            index_buffer);

        var root_bounds = new Bounds3f();
        root_bounds.update(world_extents);
        render_tree = new RenderTree(root_bounds, tree_queue, TREE_MIN_NODE_SIZE, 8);

        var command_buffer_size = DrawElementsIndirectCommand.calculate_buffer_size(patch_count);
        cbo = GPU.GL.command_buffer(resources, command_buffer_size);
        command_buffer = cbo.map_as_byte_buffer_persistent();

        float[] colors = new float[color_buffer.size() * 3];
        float[] vertices = new float[vertex_buffer.size() * 3];
        float[] normals = new float[normal_buffer.size() * 3];
        float[] tangents = new float[tangent_buffer.size() * 3];
        float[] bitangents = new float[bitangent_buffer.size() * 3];
        float[] uvs = new float[uv_buffer.size() * 2];
        int[] indices = new int[index_buffer.size()];

        for (int i = 0; i < color_buffer.size(); i++)
        {
            var color = color_buffer.get(i);
            var j = i * 3;
            colors[j] = color.x;
            colors[j + 1] = color.y;
            colors[j + 2] = color.z;
        }

        for (int i = 0; i < vertex_buffer.size(); i++)
        {
            var vertex = vertex_buffer.get(i);
            var j = i * 3;
            vertices[j] = vertex.x;
            vertices[j + 1] = vertex.y;
            vertices[j + 2] = vertex.z;
        }

        for (int i = 0; i < normal_buffer.size(); i++)
        {
            var normal = normal_buffer.get(i);
            var j = i * 3;
            normals[j] = normal.x;
            normals[j + 1] = normal.y;
            normals[j + 2] = normal.z;
        }

        for (int i = 0; i < tangent_buffer.size(); i++)
        {
            var tangent = tangent_buffer.get(i);
            var j = i * 3;
            tangents[j] = tangent.x;
            tangents[j + 1] = tangent.y;
            tangents[j + 2] = tangent.z;
        }

        for (int i = 0; i < bitangent_buffer.size(); i++)
        {
            var bitangent = bitangent_buffer.get(i);
            var j = i * 3;
            bitangents[j] = bitangent.x;
            bitangents[j + 1] = bitangent.y;
            bitangents[j + 2] = bitangent.z;
        }

        for (int i = 0; i < uv_buffer.size(); i++)
        {
            var uv = uv_buffer.get(i);
            int j = i * 2;
            uvs[j] = uv.x;
            uvs[j + 1] = uv.y;
        }

        for (int i = 0; i < index_buffer.size(); i++)
        {
            indices[i] = index_buffer.get(i);
        }

        terrain_low = GPU.GL.new_texture(resources, true, "/img/terrain_low_alt.png");
        terrain_mid = GPU.GL.new_texture(resources, true, "/img/terrain_mid_alt.png");
        terrain_high = GPU.GL.new_texture(resources, true, "/img/terrain_high_alt.png");
        terrain_top = GPU.GL.new_texture(resources, true, "/img/terrain_top_alt.png");

        terrain_low_normal = GPU.GL.new_texture(resources, false, "/img/terrain_low_alt_normal.png");
        terrain_mid_normal = GPU.GL.new_texture(resources, false, "/img/terrain_mid_alt_normal.png");
        terrain_high_normal = GPU.GL.new_texture(resources, false, "/img/terrain_high_alt_normal.png");
        terrain_top_normal = GPU.GL.new_texture(resources, false, "/img/terrain_top_alt_normal.png");

        terrain_low_height = GPU.GL.new_texture(resources, false, "/img/terrain_low_alt_height.png");
        terrain_mid_height = GPU.GL.new_texture(resources, false, "/img/terrain_mid_alt_height.png");
        terrain_high_height = GPU.GL.new_texture(resources, false, "/img/terrain_high_alt_height.png");
        terrain_top_height = GPU.GL.new_texture(resources, false, "/img/terrain_top_alt_height.png");

        vao = GPU.GL.new_vao(resources);
        shader = GPU.GL.new_shader(resources, "terrain");

        GPU.GL.element_buffer_static(resources, vao, indices);
        GPU.GL.vec3_buffer_static(resources, vao, XYZ_ATTRIBUTE, vertices);
        GPU.GL.vec3_buffer_static(resources, vao, NORMAL_ATTRIBUTE, normals);
        GPU.GL.vec3_buffer_static(resources, vao, TANGENT_ATTRIBUTE, tangents);
        GPU.GL.vec3_buffer_static(resources, vao, BITANGENT_ATTRIBUTE, bitangents);
        GPU.GL.vec2_buffer_static(resources, vao, UV_ATTRIBUTE, uvs);
        GPU.GL.vec3_buffer_static(resources, vao, COLOR_ATTRIBUTE, colors);

        vao.enable_attribute(XYZ_ATTRIBUTE);
        vao.enable_attribute(NORMAL_ATTRIBUTE);
        vao.enable_attribute(TANGENT_ATTRIBUTE);
        vao.enable_attribute(BITANGENT_ATTRIBUTE);
        vao.enable_attribute(UV_ATTRIBUTE);
        vao.enable_attribute(COLOR_ATTRIBUTE);

        shader.use();
        shader.uploadInt("terrainLow", 0);
        shader.uploadInt("terrainMid", 1);
        shader.uploadInt("terrainHigh", 2);
        shader.uploadInt("terrainTop", 3);
        shader.uploadInt("terrainLowNormal", 4);
        shader.uploadInt("terrainMidNormal", 5);
        shader.uploadInt("terrainHighNormal", 6);
        shader.uploadInt("terrainTopNormal", 7);
        shader.uploadInt("terrainLowHeight", 8);
        shader.uploadInt("terrainMidHeight", 9);
        shader.uploadInt("terrainHighHeight", 10);
        shader.uploadInt("terrainTopHeight", 11);
        shader.uploadFloat("gHeight0", terrain_ranges[0]);
        shader.uploadFloat("gHeight1", terrain_ranges[1]);
        shader.uploadFloat("gHeight2", terrain_ranges[2]);
        shader.uploadFloat("gHeight3", terrain_ranges[3]);
        shader.detach();

        if (DEBUG)
        {
            debug_setup();
        }
    }

    private record BoundsEntry(Bounds3f bounds, Vector3f color)
    {
    }

    private final Vector3f white = new Vector3f(1.0f);
    private final Vector3f green = new Vector3f(0.0f, 1.0f, 0.0f);
    private final Vector3f blue = new Vector3f(0.0f, 0.0f, 1.0f);

    private List<BoundsEntry> get_octree_aabbs()
    {
        List<BoundsEntry> bounds = new ArrayList<>();
        bounds.add(new BoundsEntry(render_tree.bounds, white));
        collectNodes(bounds, render_tree.root);
        return bounds;
    }

    public void collectNodes(List<BoundsEntry> nodeList, RenderNode node)
    {
        nodeList.add(new BoundsEntry(node.bounds, blue));
        node.objects.forEach(o -> nodeList.add(new BoundsEntry(o, green)));
        if (node.children != null)
        {
            for (RenderNode child : node.children)
            {
                if (child != null)
                {
                    collectNodes(nodeList, child);
                }
            }
        }
    }

    private void update_hull_data(List<BoundsEntry> octree_bounds)
    {
        float[] min_verts = new float[dbg_box_count * 3];
        float[] max_verts = new float[dbg_box_count * 3];
        float[] colors = new float[dbg_box_count * 3];

        int buffer_index = 0;

        for (var aabb : octree_bounds)
        {
            int x = buffer_index++;
            int y = buffer_index++;
            int z = buffer_index++;

            min_verts[x] = aabb.bounds.min.x;
            min_verts[y] = aabb.bounds.min.y;
            min_verts[z] = aabb.bounds.min.z;

            max_verts[x] = aabb.bounds.max.x;
            max_verts[y] = aabb.bounds.max.y;
            max_verts[z] = aabb.bounds.max.z;

            colors[x] = aabb.color.x;
            colors[y] = aabb.color.y;
            colors[z] = aabb.color.z;
        }

        dbg_min_vbo_buffer.clear();
        dbg_max_vbo_buffer.clear();
        dbg_color_vbo_buffer.clear();
        dbg_min_vbo_buffer.put(min_verts);
        dbg_max_vbo_buffer.put(max_verts);
        dbg_color_vbo_buffer.put(colors);
    }

    private void debug_setup()
    {
        var octree_aabbs = get_octree_aabbs();
        dbg_box_count = octree_aabbs.size();

        dbg_shader = GPU.GL.new_shader(resources, "render_aabb");
        dbg_vao = GPU.GL.new_vao(resources);
        dbg_min_vbo = GPU.GL.vec3_buffer(resources, dbg_vao, DBG_MIN_ATTRIBUTE, dbg_box_count * VECTOR_FLOAT_3D_SIZE);
        dbg_max_vbo = GPU.GL.vec3_buffer(resources, dbg_vao, DBG_MAX_ATTRIBUTE, dbg_box_count * VECTOR_FLOAT_3D_SIZE);
        dbg_color_vbo = GPU.GL.vec3_buffer(resources, dbg_vao, DBG_COLOR_ATTRIBUTE, dbg_box_count * VECTOR_FLOAT_3D_SIZE);

        dbg_min_vbo_buffer = dbg_min_vbo.map_as_float_buffer_persistent();
        dbg_max_vbo_buffer = dbg_max_vbo.map_as_float_buffer_persistent();
        dbg_color_vbo_buffer = dbg_color_vbo.map_as_float_buffer_persistent();

        dbg_vao.enable_attribute(DBG_MIN_ATTRIBUTE);
        dbg_vao.enable_attribute(DBG_MAX_ATTRIBUTE);
        dbg_vao.enable_attribute(DBG_COLOR_ATTRIBUTE);

        update_hull_data(octree_aabbs);
    }

    public static float[] generate_height_intervals(float bottom_range, float top_range, float floor)
    {
        float low_end = bottom_range + floor;
        float top_end = top_range + floor;
        float spread = top_end - low_end;

        float h0 = low_end + spread * 0.408f;
        float h1 = low_end + spread * 0.47466f;
        float h2 = low_end + spread * 0.608f;
        float h3 = low_end + spread * 0.67466f;

        return new float[]{h0, h1, h2, h3};
    }


    // todo: this could be separated out to a util class and have tests added
    int generate_terrain_grid(Queue<Bounds3f> tree_queue,
                              RenderExtents world_extents,
                              List<Vector3f> colors,
                              List<Vector3f> vertices,
                              List<Vector3f> normals,
                              List<Vector3f> tangents,
                              List<Vector3f> bitangents,
                              List<Vector2f> uvs,
                              List<Integer> indices)
    {
        colors.clear();
        vertices.clear();
        normals.clear();
        tangents.clear();
        bitangents.clear();
        uvs.clear();
        indices.clear();

        float halfSize = (GRID_SIZE * CELL_SIZE) * 0.5f;

        int patch_count = 0;

        for (int latitude = 0; latitude <= GRID_SIZE; latitude++)
        {
            for (int longitude = 0; longitude <= GRID_SIZE; longitude++)
            {
                normals.add(new Vector3f());
                tangents.add(new Vector3f());
                bitangents.add(new Vector3f());

                float x = longitude * CELL_SIZE - halfSize;
                float z = latitude * CELL_SIZE - halfSize;

                float n = noise.GetNoise(x, z);
                float altitude = MathEX.map(n, -1, 1, BOTTOM_RANGE, TOP_RANGE);
                float y = TerrainRenderPass.FLOOR + altitude;
                var vector = new Vector3f(x, y, z);
                vertices.add(vector);
                world_extents.process(vector);

                float u = ((float) longitude / GRID_SIZE) * UV_TILING;
                float v = ((float) latitude / GRID_SIZE) * UV_TILING;
                uvs.add(new Vector2f(u, v));

                float hue = choose_hue(y);
                float saturation = S_noise.GetNoise(x, z);
                float value = V_noise.GetNoise(x, z);

                saturation = MathEX.map(saturation, -1, 1, 0.5f, 1f);
                value = MathEX.map(value, -1, 1, 0.0f, 1f);

                float[] rgb = MathEX.hsv_to_normalized_rgb(hue, saturation, value, true);
                colors.add(new Vector3f(rgb[0], rgb[1], rgb[2]));
            }
        }

        var patch_extents = new RenderExtents();

        for (int latitude = 0; latitude <= GRID_SIZE - 1; latitude++)
        {
            for (int longitude = 0; longitude <= GRID_SIZE - 1; longitude++)
            {
                patch_count++;
                int top_L = latitude * (GRID_SIZE + 1) + longitude;
                int top_R = top_L + 1;
                int bot_L = (latitude + 1) * (GRID_SIZE + 1) + longitude;
                int bot_R = bot_L + 1;

                long offset = indices.size();

                indices.add(top_L);
                indices.add(bot_L);
                indices.add(top_R);
                indices.add(bot_R);

                patch_extents.reset();

                patch_extents.process(vertices.get(top_L));
                patch_extents.process(vertices.get(bot_L));
                patch_extents.process(vertices.get(top_R));
                patch_extents.process(vertices.get(bot_R));

                var bounds = new Bounds3f(null, offset);
                bounds.min.set(patch_extents.min_x, patch_extents.min_y, patch_extents.min_z);
                bounds.max.set(patch_extents.max_x, patch_extents.max_y, patch_extents.max_z);
                bounds.update_derived();
                tree_queue.add(bounds);
            }
        }

        int index_set_size = indices.size() / 4;
        for (int i = 0; i < index_set_size; i++)
        {
            int set_index = i * 4;
            int a_index = indices.get(set_index);
            int b_index = indices.get(set_index + 1);
            int c_index = indices.get(set_index + 2);
            int d_index = indices.get(set_index + 3);

            calculate_TBN(a_index, b_index, c_index, vertices, normals, tangents, bitangents, uvs);
            calculate_TBN(b_index, d_index, c_index, vertices, normals, tangents, bitangents, uvs);
        }

        for (int i = 0; i < normals.size(); i++)
        {
            normals.get(i).normalize();
            tangents.get(i).normalize();
            bitangents.get(i).normalize();
        }

        return patch_count;
    }

    private void calculate_TBN(int p1, int p2, int p3,
                               List<Vector3f> vertices,
                               List<Vector3f> normals,
                               List<Vector3f> tangents,
                               List<Vector3f> bitangents,
                               List<Vector2f> uvs)
    {
        var a = vertices.get(p1);
        var b = vertices.get(p2);
        var c = vertices.get(p3);

        var uva = uvs.get(p1);
        var uvb = uvs.get(p2);
        var uvc = uvs.get(p3);

        b.sub(a, edge1_buffer);
        c.sub(a, edge2_buffer);
        edge1_buffer.cross(edge2_buffer, normal_buffer);
        normal_buffer.normalize();

        normals.get(p1).add(normal_buffer);
        normals.get(p2).add(normal_buffer);
        normals.get(p3).add(normal_buffer);

        uvb.sub(uva, uv1_buffer);
        uvc.sub(uva, uv2_buffer);

        float f = 1.0f / (uv1_buffer.x * uv2_buffer.y - uv1_buffer.y * uv2_buffer.x);

        tangent_buffer.x = f * (edge1_buffer.x * uv2_buffer.y - edge2_buffer.x * uv1_buffer.y);
        tangent_buffer.y = f * (edge1_buffer.y * uv2_buffer.y - edge2_buffer.y * uv1_buffer.y);
        tangent_buffer.z = f * (edge1_buffer.z * uv2_buffer.y - edge2_buffer.z * uv1_buffer.y);
        tangent_buffer.normalize();

        bitangent_buffer.x = f * (edge2_buffer.x * uv1_buffer.x - edge1_buffer.x * uv2_buffer.x);
        bitangent_buffer.y = f * (edge2_buffer.y * uv1_buffer.x - edge1_buffer.y * uv2_buffer.x);
        bitangent_buffer.z = f * (edge2_buffer.z * uv1_buffer.x - edge1_buffer.z * uv2_buffer.x);
        bitangent_buffer.normalize();

        tangents.get(p1).add(tangent_buffer);
        tangents.get(p2).add(tangent_buffer);
        tangents.get(p3).add(tangent_buffer);

        bitangents.get(p1).add(bitangent_buffer);
        bitangents.get(p2).add(bitangent_buffer);
        bitangents.get(p3).add(bitangent_buffer);
    }

    private float choose_hue(float height)
    {
        if (height < terrain_ranges[0])
        {
            return 200;
        }
        else if (height < terrain_ranges[1])
        {
            return 50;
        }
        else if (height < terrain_ranges[2])
        {
            return 220;
        }
        else
        {
            return 50;
        }
    }

    private void resize_off_heap_buffer()
    {
        if (render_patch_count > max_render_patch_count)
        {
            max_render_patch_count = render_patch_count;
            if (memory_arena != null)
            {
                memory_arena.close();
            }
            memory_arena = Arena.ofConfined();
            command_segment = memory_arena.allocate(DrawElementsIndirectCommand.LAYOUT, render_patch_count);
        }
    }

    private void update_command()
    {
        var visible_patches = render_tree.query(camera.frustum());
        render_patch_count = visible_patches.size();
        long current_patch_index = 0;
        command_buffer.clear();
        resize_off_heap_buffer();
        for (var index : visible_patches)
        {
            DrawElementsIndirectCommand.map_at_index(command_segment,
                current_patch_index,
                PATCH_INDEX_COUNT,
                PATCH_INSTANCE_COUNT,
                (int) index.id,
                PATCH_BASE_VERTEX,
                (int) current_patch_index);
            current_patch_index++;
        }
        command_buffer.put(0, command_segment.asByteBuffer(), 0, (int) DrawElementsIndirectCommand.LAYOUT.byteSize() * render_patch_count);
    }

    private void bind_textures()
    {
        terrain_low.bind(0);
        terrain_mid.bind(1);
        terrain_high.bind(2);
        terrain_top.bind(3);
        terrain_low_normal.bind(4);
        terrain_mid_normal.bind(5);
        terrain_high_normal.bind(6);
        terrain_top_normal.bind(7);
        terrain_low_height.bind(8);
        terrain_mid_height.bind(9);
        terrain_high_height.bind(10);
        terrain_top_height.bind(11);
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
        update_command();
        glPatchParameteri(GL_PATCH_VERTICES, 4);
        vao.bind();
        cbo.bind();
        shader.use();
        shader.uploadFloat("uTime", time_index.value);
        bind_textures();
        glMultiDrawElementsIndirect(GL_PATCHES, GL_UNSIGNED_INT, 0, render_patch_count, 0);
        shader.detach();
        vao.unbind();

        if (DEBUG)
        {
            dbg_vao.bind();
            dbg_shader.use();
            glDrawArrays(GL_POINTS, 0, dbg_box_count);
            dbg_shader.detach();
            dbg_vao.unbind();
        }
    }
}
