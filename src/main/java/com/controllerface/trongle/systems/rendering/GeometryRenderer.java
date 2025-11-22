package com.controllerface.trongle.systems.rendering;

import com.controllerface.trongle.components.Component;
import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.ecs.ECSWorld;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.Renderer;
import com.controllerface.trongle.struct.GlobalLight;
import com.controllerface.trongle.systems.rendering.passes.base.*;
import com.controllerface.trongle.systems.rendering.passes.debug.ShadowDebugPass;
import com.juncture.alloy.rendering.RenderComponent;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

public class GeometryRenderer extends Renderer
{
    private static final boolean DEBUG = false;

    private final RenderPass shadow_debug_pass;

    private final ByteBuffer ubo_global_light;

    private final Arena memory_arena = Arena.ofConfined();
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
        SKYBOX_DAY_TEXTURE("skybox"),
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

    protected final ECSLayer<Component> ecs;
    protected final ECSLayer<RenderComponent> recs;

    public GeometryRenderer(ECSWorld world)
    {
        ecs = world.get(Component.class);
        recs = world.get(RenderComponent.class);

        var skybox_texture = GPU.GL.new_cube_map(resources,
            "/img/skybox/sh_ft.png",
            "/img/skybox/sh_bk.png",
            "/img/skybox/sh_up.png",
            "/img/skybox/sh_dn.png",
            "/img/skybox/sh_rt.png",
            "/img/skybox/sh_lf.png");

        var skybox_texture_dark = GPU.GL.new_cube_map(resources,
            "/img/skybox/sh_ft_dark.png",
            "/img/skybox/sh_bk_dark.png",
            "/img/skybox/sh_up_dark.png",
            "/img/skybox/sh_dn_dark.png",
            "/img/skybox/sh_rt_dark.png",
            "/img/skybox/sh_lf_dark.png");


        var shadow_map_texture = GPU.GL.new_shadow_texture(resources);

        add_pass(new LightRenderPass(ecs, recs));
        add_pass(new ModelRenderPass(recs, skybox_texture, skybox_texture_dark, shadow_map_texture));
        //add_pass(new HitScanWeaponRenderPass(ecs));
        //add_pass(new TerrainRenderPass(ecs));
        //add_pass(new ParticleRenderPass(ecs));
        //add_pass(new ExplosionRenderPass(ecs));
        //add_pass(new CloudRenderPass(ecs, shadow_map_texture));
        add_pass(new SkyboxRenderPass(skybox_texture, skybox_texture_dark));

        var glb = GPU.GL.uniform_buffer(resources, GlobalLight.LAYOUT.byteSize());
        glb.bind(UBO_BindPoint.GLOBAL_LIGHT.ordinal());
        ubo_global_light = glb.map_as_byte_buffer();

        glb_segment = memory_arena.allocate(GlobalLight.LAYOUT);

        shadow_debug_pass = DEBUG ? new ShadowDebugPass(ecs, recs, shadow_map_texture) : null;
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
        GlobalLight.ecs_map_at_index(glb_segment, 0, ecs, recs);
        ubo_global_light.put(glb_segment.asByteBuffer());

        super.render();

        if (DEBUG) shadow_debug_pass.render();
    }
}
