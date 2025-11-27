package com.controllerface.trongle.main;

import com.controllerface.trongle.systems.rendering.DebugHUDRenderer;
import com.juncture.alloy.base.CameraSystem;
import com.juncture.alloy.base.ParticleSystem;
import com.juncture.alloy.base.TransformUpdateSystem;
import com.juncture.alloy.base.debug.*;
import com.juncture.alloy.camera.WorldCamera;
import com.juncture.alloy.data.*;
import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.ecs.ECSSystem;
import com.juncture.alloy.ecs.ECSWorld;
import com.juncture.alloy.ecs.GameMode;
import com.juncture.alloy.events.Event;
import com.juncture.alloy.events.EventBus;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.GPUResourceGroup;
import com.juncture.alloy.gpu.RenderPass;
import com.juncture.alloy.gpu.gl.GL_GraphicsController;
import com.juncture.alloy.rendering.*;
import com.juncture.alloy.physics.PhysicsComponent;
import com.juncture.alloy.physics.PhysicsSystem;
import com.juncture.alloy.physics.PhysicsTypes;
import com.juncture.alloy.rendering.camera.LightSpaceSystem;
import com.juncture.alloy.utils.math.MathEX;
import com.juncture.alloy.utils.math.Ray3d;
import com.controllerface.trongle.components.*;
import com.controllerface.trongle.events.GameEvent;
import com.controllerface.trongle.events.ModeSwitchEvent;
import com.controllerface.trongle.systems.behavior.EntityBehaviorSystem;
import com.controllerface.trongle.systems.input.InputBinding;
import com.controllerface.trongle.systems.input.InputState;
import com.controllerface.trongle.systems.UpkeepSystem;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class BaseGame extends GameMode
{
    private static final boolean DEBUG_MODE = false;

    private static final float CENTER_PITCH = -90f;
    private static final float CENTER_YAW = 0f;
    private static final float PITCH_RANGE = 15f;
    private static final float YAW_RANGE = 25f;
    private static final float ZOOM_MIN = 5f;
    private static final float ZOOM_MAX = 500f;
    private static final float ZOOM_SPEED = 10.0f;
    private static final float ZOOM_RATE = 0.001f;

    private final Queue<Event> event_queue = new LinkedBlockingQueue<>();

    private final Duration DAY_LENGTH = Duration.ofMinutes(15);

    private final Vector3f sun_direction = new Vector3f(0.0f, -1.0f, 0.0f);
    private final Vector3f moon_direction = new Vector3f(0.0f, 1.0f, 0.0f);
    private final MutableFloat time_of_day = new MutableFloat(0.0f);

    private float sun_angle = 0.0f;
    private float day_speed = 0.1f;

    private String player_entity;

    private InputState input_state;
    private final EventBus event_bus;
    private boolean latched = false;

    private final GL_GraphicsController gl_controller;

    private final ECSLayer<Component> ecs;
    private final ECSLayer<PhysicsComponent> pecs;
    private final ECSLayer<RenderComponent> recs;

    private final List<ECSSystem> systems = new ArrayList<>();

    private final MutableFloat time_index = new MutableFloat(0.0f);

    public BaseGame(ECSWorld world, GL_GraphicsController glController)
    {
        super(world);
        event_bus = world.event_bus;
        ecs = world.get(Component.class);
        pecs = world.get(PhysicsComponent.class);
        recs = world.get(RenderComponent.class);
        gl_controller = glController;
    }

    private void cycle_day_night(double dt)
    {
        sun_angle += (float) (day_speed * dt);

        if (sun_angle > Math.PI)
        {
            sun_angle -= (float) (2.0f * Math.PI);
        }
        else if (sun_angle < -Math.PI)
        {
            sun_angle += (float) (2.0f * Math.PI);
        }

        float vertical = (float) -Math.cos(sun_angle);
        float horizontal = (float) -Math.sin(sun_angle);

        time_of_day.value = MathEX.map(vertical, -1.0f, 1.0f, 0.0f, 1.0f);

        sun_direction.set(horizontal, vertical, horizontal).normalize();
        sun_direction.negate(moon_direction);
    }

    public static float calculate_day_speed(Duration day_duration)
    {
        var day_seconds = day_duration.toSeconds();
        return (float) (2.0 * Math.PI / day_seconds);
    }

    private void process_key_state()
    {
        boolean escape = input_state.is_active(InputBinding.ESCAPE);
        if (escape && !latched)
        {
            latched = true;
            event_bus.emit_event(new ModeSwitchEvent());
        }
        else if (!escape)
        {
            latched = false;
        }
    }

    protected final GPUResourceGroup resources = new GPUResourceGroup();


    @Override
    public void update(double dt)
    {
        process_key_state();
        cycle_day_night(dt);
        time_index.value += (float) dt;
    }

    @Override
    public void activate()
    {
        latched = true;
        systems.forEach(world::register_system);
    }

    @Override
    public void deactivate()
    {
        systems.forEach(world::deregister_system);
    }

    @Override
    public void init()
    {
        input_state = Component.Input.global(ecs);
        world.event_bus.register(event_queue, GameEvent.ENTITY_DESTROYED);
        recs.set_global(RenderComponent.TimeIndex, time_index);

        // Mouse

        var mouse_ray = Ray3d.generate_empty(player_entity);
        var mouse_ray_entity = world.new_entity();
        PhysicsTypes.mouse_target(pecs, mouse_ray_entity, mouse_ray);
        pecs.set_global(PhysicsComponent.MouseRay, mouse_ray_entity);

        // Time of Day

        day_speed = calculate_day_speed(DAY_LENGTH);
        recs.set_global(RenderComponent.TimeOfDay, time_of_day);

        var sun_light_entity = world.new_entity();
        recs.set_component(sun_light_entity, RenderComponent.SunLight, Marker.MARKED);
        recs.set_component(sun_light_entity, RenderComponent.Color, new Vector4f(0.93f, 0.90f, 0.71f, 1.0f));
        recs.set_component(sun_light_entity, RenderComponent.Direction, sun_direction);
        recs.set_component(sun_light_entity, RenderComponent.LightIntensity, new LightIntensity(0.005f, 0.5f));

        var moon_light_entity = world.new_entity();
        recs.set_component(moon_light_entity, RenderComponent.MoonLight, Marker.MARKED);
        recs.set_component(moon_light_entity, RenderComponent.Color, new Vector4f(0.04f, 0.07f, 0.32f, 1.0f));
        recs.set_component(moon_light_entity, RenderComponent.Direction, moon_direction);
        recs.set_component(moon_light_entity, RenderComponent.LightIntensity, new LightIntensity(0.007f, 0.2f));

        // Player

        player_entity = world.new_entity();

        var model = GLTFModel.TEST_CUBE;
        var scale = 1.0f;
        var mass = 5.0f;
        var drag = 1.0f;
        var max_pitch = 20.0f;
        var max_roll = 25.0f;
        var max_thrust = 1000.0f;
        var max_yaw = 10.0f;
        var inertia = 1.0f;

        var max_speed = max_thrust / (mass * drag);
        var max_ang_speed = max_yaw / (inertia * drag);

        var lights = RenderTypes.model_lights(world, recs, model, scale);
        var hulls = RenderTypes.model_hulls(recs, model, player_entity);

        Archetypes.player(ecs, recs, player_entity);
        RenderTypes.model(recs, player_entity, model, lights);
        PhysicsTypes.physics(pecs, player_entity, mass, inertia, drag,
            max_thrust, max_yaw, max_pitch, max_roll, max_speed, max_ang_speed,
            new Vector3d(0.0), new Vector3d(0f, -(Math.PI / 2), 0f), new Vector3d(scale),
            hulls);


        // Systems
        systems.add(new PhysicsSystem(world));
        systems.add(new TransformUpdateSystem(world));
        systems.add(new EntityBehaviorSystem(world));
        //systems.add(new ParticleSystem(world));
        systems.add(new CameraSystem(world));
        systems.add(new LightSpaceSystem(world));


        var skybox_texture = GPU.GL.new_cube_map(resources,
            "/img/skybox/sh_ft.png",
            "/img/skybox/sh_bk.png",
            "/img/skybox/sh_up.png",
            "/img/skybox/sh_dn.png",
            "/img/skybox/sh_rt.png",
            "/img/skybox/sh_lf.png");
//
        var skybox_texture_dark = GPU.GL.new_cube_map(resources,
            "/img/skybox/sh_ft_dark.png",
            "/img/skybox/sh_bk_dark.png",
            "/img/skybox/sh_up_dark.png",
            "/img/skybox/sh_dn_dark.png",
            "/img/skybox/sh_rt_dark.png",
            "/img/skybox/sh_lf_dark.png");
//
//
        var shadow_map_texture = GPU.GL.new_shadow_texture(resources);

        var terrain_metrics = new TerrainMetrics(
            128,
            512.0f,
            512.0f,
            0.0f,
            -1000.0f,
            500.0f,
            512.0f * 8
        );

        systems.add(new RenderingSystem<>(world, gl_controller, List.of(
            new LightRenderPass(recs),
            new TerrainRenderPass(recs, terrain_metrics),
            new ModelRenderPass<>(recs, gl_controller, GLTFModel.class, skybox_texture, skybox_texture_dark, shadow_map_texture),
            new ParticleRenderPass(world),
            new SkyboxRenderPass(skybox_texture, skybox_texture_dark),
            new CloudRenderPass(recs, shadow_map_texture, 2000, 32_768.0f)
            )));

        systems.add(new UpkeepSystem(world));

        // Camera

        var camera = RenderComponent.MainCamera.<WorldCamera>global(recs);
        camera.set_pitch_range(CENTER_PITCH - PITCH_RANGE, CENTER_PITCH + PITCH_RANGE);
        camera.set_yaw_range(CENTER_YAW - YAW_RANGE, CENTER_YAW + YAW_RANGE);
        camera.set_zoom_speed_limits(ZOOM_SPEED, ZOOM_RATE);
        camera.set_zoom_distance_limits(ZOOM_MIN, ZOOM_MAX);
        camera.set_pitch(CENTER_PITCH);
        camera.set_yaw(CENTER_YAW);

        // for debugging todo: move below code to a proper debug start up process
        if (DEBUG_MODE)
        {
            var passes = List.<RenderPass>of(
                new ConvexHullRenderPass(pecs),
                new RayCastRenderPass(pecs),
                new BoundingBoxRenderPass(pecs, recs),
                new RenderingBoundsRenderPass(recs),
                new DebugHUDRenderer(world)
            );
            systems.add(new DebugRenderingSystem(world, passes));
        }
    }

    @Override
    public void destroy()
    {
        // todo: game save or something
        resources.release_all();
    }
}
