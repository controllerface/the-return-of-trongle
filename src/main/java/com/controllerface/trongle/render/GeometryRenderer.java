package com.controllerface.trongle.render;

import com.controllerface.trongle.Component;
import com.controllerface.trongle.struct.GlobalLight;
import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.Renderer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

public class GeometryRenderer extends Renderer<Component>
{
    private final Arena memory_arena = Arena.ofConfined();
    private final ByteBuffer ubo_global_light;
    private final MemorySegment glb_segment;

    public enum UBO_BindPoint
    {
        VIEW_DATA,
        GLOBAL_LIGHT,
    }

    public enum SSBO_BindPoint
    {
        POINT_LIGHT,
        SPOT_LIGHT,
        MESH_TRANSFORM,
        MODEL_TRANSFORM,
    }

    public enum Texture_BindPoint
    {
        DIFFUSE_MAP("diffuseMaps"),
        SURFACE_MAP("surfaceMaps"),
        NORMAL_MAP("normalMaps"),
        SKYBOX_DAY_TEXTURE("img/skybox"),
        SKYBOX_NIGHT_TEXTURE("skyboxDark"),
        SHADOW_MAP("shadowMap"),
        BACKGROUND("bgTexture"),

        ;

        public final String varName;

        Texture_BindPoint(String varName)
        {
            this.varName = varName;
        }
    }

    protected GeometryRenderer(ECS<Component> ecs)
    {
        super(ecs);

        var skybox_texture = GPU.GL.new_cube_map(resources,
            "/img/skybox/sh_rt.png",
            "/img/skybox/sh_lf.png",
            "/img/skybox/sh_up.png",
            "/img/skybox/sh_dn.png",
            "/img/skybox/sh_ft.png",
            "/img/skybox/sh_bk.png");

        var skybox_texture_dark = GPU.GL.new_cube_map(resources,
            "/img/skybox/sh_rt.png",
            "/img/skybox/sh_lf.png",
            "/img/skybox/sh_up.png",
            "/img/skybox/sh_dn.png",
            "/img/skybox/sh_ft.png",
            "/img/skybox/sh_bk.png");

        var shadow_map_texture = GPU.GL.new_shadow_texture(resources);

        add_pass(new LightRenderPass(ecs));
        add_pass(new ModelRenderPass(ecs, skybox_texture, skybox_texture_dark, shadow_map_texture));
        add_pass(new SkyboxRenderPass(skybox_texture, skybox_texture_dark));

        var glb = GPU.GL.uniform_buffer(resources, GlobalLight.LAYOUT.byteSize());
        glb.bind(UBO_BindPoint.GLOBAL_LIGHT.ordinal());
        ubo_global_light = glb.map_as_byte_buffer();
        glb_segment = memory_arena.allocate(GlobalLight.LAYOUT);
    }

    @Override
    public void destroy()
    {
        super.destroy();
        memory_arena.close();
    }

    @Override
    public void render()
    {
        ubo_global_light.clear();
        GlobalLight.ecs_map_at_index(glb_segment, 0, ecs);
        ubo_global_light.put(glb_segment.asByteBuffer());

        super.render();
    }
}
