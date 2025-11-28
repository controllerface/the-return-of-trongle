package com.controllerface.trongle.main;

import com.controllerface.trongle.behavior.TrongleEntityBehavior;
import com.controllerface.trongle.events.GameEvent;
import com.controllerface.trongle.events.ModeSwitchEvent;
import com.controllerface.trongle.systems.UpkeepSystem;
import com.controllerface.trongle.input.InputBinding;
import com.controllerface.trongle.input.InputState;
import com.juncture.alloy.base.CameraSystem;
import com.juncture.alloy.base.EntityBehaviorSystem;
import com.juncture.alloy.base.TransformUpdateSystem;
import com.juncture.alloy.base.debug.*;
import com.juncture.alloy.camera.WorldCamera;
import com.juncture.alloy.data.LightIntensity;
import com.juncture.alloy.data.Marker;
import com.juncture.alloy.data.MutableFloat;
import com.juncture.alloy.ecs.*;
import com.juncture.alloy.events.CoreEvent;
import com.juncture.alloy.events.Event;
import com.juncture.alloy.events.EventBus;
import com.juncture.alloy.events.MessageEvent;
import com.juncture.alloy.events.debug.DebugEvent;
import com.juncture.alloy.events.debug.PositionEvent;
import com.juncture.alloy.events.debug.ViewDebugEvent;
import com.juncture.alloy.gpu.GPU;
import com.juncture.alloy.gpu.GPUResourceGroup;
import com.juncture.alloy.gpu.gl.GL_GraphicsController;
import com.juncture.alloy.physics.PhysicsComponent;
import com.juncture.alloy.physics.PhysicsSystem;
import com.juncture.alloy.physics.PhysicsTypes;
import com.juncture.alloy.rendering.*;
import com.juncture.alloy.rendering.camera.LightSpaceSystem;
import com.juncture.alloy.ui.SnapPosition;
import com.juncture.alloy.ui.TextContainer;
import com.juncture.alloy.utils.math.MathEX;
import com.juncture.alloy.utils.math.Ray3d;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class BaseGame extends GameMode
{
    private static final boolean DEBUG_MODE = true;

    private static final float CENTER_PITCH = -90f;
    private static final float CENTER_YAW   =  0f;
    private static final float PITCH_RANGE  =  15f;
    private static final float YAW_RANGE    =  25f;
    private static final float ZOOM_MIN     =  5f;
    private static final float ZOOM_MAX     =  500f;
    private static final float ZOOM_SPEED   =  10.0f;
    private static final float ZOOM_RATE    =  0.001f;

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

    private final ECSLayer<BaseComponent>    base_layer;
    private final ECSLayer<PhysicsComponent> phys_layer;
    private final ECSLayer<RenderComponent>  rend_layer;
    private final List<ECSSystem> systems = new ArrayList<>();

    private final MutableFloat time_index = new MutableFloat(0.0f);

    private final GPUResourceGroup resources = new GPUResourceGroup();

    public BaseGame(ECSWorld world, GL_GraphicsController glController)
    {
        super(world);
        event_bus = world.event_bus;
        base_layer = world.get(BaseComponent.class);
        phys_layer = world.get(PhysicsComponent.class);
        rend_layer = world.get(RenderComponent.class);
        gl_controller = glController;
    }

    @Override
    public void update(double dt)
    {
        time_index.value += (float) dt;


        // Process events

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


        // Day/Night Cycle

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
        // Misc

        input_state = BaseComponent.Input.global(base_layer);
        world.event_bus.register(event_queue, GameEvent.ENTITY_DESTROYED);
        base_layer.set_global(BaseComponent.TimeIndex, time_index);

        // Camera

        var camera = RenderComponent.MainCamera.<WorldCamera>global(rend_layer);
        camera.set_pitch_range(CENTER_PITCH - PITCH_RANGE, CENTER_PITCH + PITCH_RANGE);
        camera.set_yaw_range(CENTER_YAW - YAW_RANGE, CENTER_YAW + YAW_RANGE);
        camera.set_zoom_speed_limits(ZOOM_SPEED, ZOOM_RATE);
        camera.set_zoom_distance_limits(ZOOM_MIN, ZOOM_MAX);
        camera.set_pitch(CENTER_PITCH);
        camera.set_yaw(CENTER_YAW);

        // Mouse

        var mouse_ray = Ray3d.generate_empty(player_entity);
        var mouse_ray_entity = world.new_entity();
        PhysicsTypes.mouse_target(phys_layer, mouse_ray_entity, mouse_ray);
        phys_layer.set_global(PhysicsComponent.MouseRay, mouse_ray_entity);

        // Time of Day
        day_speed = (float) (2.0 * Math.PI / DAY_LENGTH.toSeconds());
        rend_layer.set_global(RenderComponent.TimeOfDay, time_of_day);

        var sun_light_entity = world.new_entity();
        rend_layer.set_component(sun_light_entity, RenderComponent.SunLight, Marker.MARKED);
        rend_layer.set_component(sun_light_entity, RenderComponent.Color, new Vector4f(0.93f, 0.90f, 0.71f, 1.0f));
        rend_layer.set_component(sun_light_entity, RenderComponent.Direction, sun_direction);
        rend_layer.set_component(sun_light_entity, RenderComponent.LightIntensity, new LightIntensity(0.005f, 0.5f));

        var moon_light_entity = world.new_entity();
        rend_layer.set_component(moon_light_entity, RenderComponent.MoonLight, Marker.MARKED);
        rend_layer.set_component(moon_light_entity, RenderComponent.Color, new Vector4f(0.04f, 0.07f, 0.32f, 1.0f));
        rend_layer.set_component(moon_light_entity, RenderComponent.Direction, moon_direction);
        rend_layer.set_component(moon_light_entity, RenderComponent.LightIntensity, new LightIntensity(0.007f, 0.2f));

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

        var lights = RenderTypes.model_lights(world, rend_layer, model, scale);
        var hulls = RenderTypes.model_hulls(rend_layer, model, player_entity);

        rend_layer.set_component(player_entity, RenderComponent.CameraFollow, Marker.MARKED);
        base_layer.set_component(player_entity, BaseComponent.Player,   Marker.MARKED);
        base_layer.set_component(player_entity, BaseComponent.Behavior, TrongleEntityBehavior.PLAYER);

        RenderTypes.model(rend_layer, player_entity, model, lights);
        PhysicsTypes.physics(phys_layer, player_entity, mass, inertia, drag,
            max_thrust, max_yaw, max_pitch, max_roll, max_speed, max_ang_speed,
            new Vector3d(0.0), new Vector3d(0f, -(Math.PI / 2), 0f), new Vector3d(scale),
            hulls);

        // Systems
        systems.add(new PhysicsSystem(world));
        systems.add(new TransformUpdateSystem(world));
        systems.add(new EntityBehaviorSystem(world));
        systems.add(new CameraSystem(world));
        systems.add(new LightSpaceSystem(world));

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

        var terrain_metrics = new TerrainMetrics(
            128,
            512.0f,
            2048.0f,
            0.0f,
            -1000.0f,
            500.0f,
            512.0f * 8
        );

        var render_passes = List.of(
            new LightRenderPass(rend_layer),
            new TerrainRenderPass(base_layer, rend_layer, terrain_metrics),
            new ModelRenderPass<>(rend_layer, GLTFModel.class, skybox_texture, skybox_texture_dark, shadow_map_texture),
            new SkyboxRenderPass(skybox_texture, skybox_texture_dark),
            new CloudRenderPass(base_layer, rend_layer, shadow_map_texture, 2000, 32_768.0f)
        );

        systems.add(new RenderingSystem<>(world, gl_controller, render_passes));

        systems.add(new UpkeepSystem(world));

        // for debugging todo: move below code to a proper debug start up process
        if (DEBUG_MODE)
        {
            var passes = List.of(
                new ConvexHullRenderPass(phys_layer),
                new RayCastRenderPass(phys_layer),
                new BoundingBoxRenderPass(phys_layer, rend_layer),
                new RenderingBoundsRenderPass(rend_layer),

                new DebugHUDRenderer(world,
                    this::debug_hud_setup,
                    this::process_hud_event,
                    CoreEvent.WINDOW_RESIZE,
                    CoreEvent.FPS,
                    DebugEvent.VIEW_PITCH,
                    DebugEvent.VIEW_YAW,
                    DebugEvent.VIEW_DIST,
                    DebugEvent.CAMERA_POSITION,
                    DebugEvent.PLAYER_POSITION)
            );
            systems.add(new DebugRenderingSystem(world, passes));
        }
    }


    private void debug_hud_setup(Map<String, TextContainer> text_boxes)
    {
        text_boxes.put("title", new TextContainer(SnapPosition.BOTTOM_LEFT,
            "The Return of Trongle - Prototype", 100, 100, .75f));


        text_boxes.put("fps_label", new TextContainer(SnapPosition.BOTTOM_RIGHT,
            "FPS", 100, 100, .75f));

        text_boxes.put("fps", new TextContainer(SnapPosition.BOTTOM_RIGHT,
            "-", 100, 150, .75f));


        text_boxes.put("DEBUG", new TextContainer(SnapPosition.TOP_LEFT,
            "DEBUG:", 100, 100, .75f));


        text_boxes.put("pitch_label", new TextContainer(SnapPosition.TOP_LEFT,
            "- pitch: ", 100, 150, .75f));

        text_boxes.put("pitch", new TextContainer(SnapPosition.TOP_LEFT,
            "0", 350, 150, .75f));


        text_boxes.put("yaw_label", new TextContainer(SnapPosition.TOP_LEFT,
            "- yaw:  ", 100, 200, .75f));

        text_boxes.put("yaw", new TextContainer(SnapPosition.TOP_LEFT,
            "0", 350, 200, .75f));


        text_boxes.put("dist_label", new TextContainer(SnapPosition.TOP_LEFT,
            "- dist:  ", 100, 250, .75f));

        text_boxes.put("dist", new TextContainer(SnapPosition.TOP_LEFT,
            "0", 350, 250, .75f));


        text_boxes.put("1_position_label", new TextContainer(SnapPosition.TOP_LEFT,
            "- pos 1:  ", 100, 300, .75f));

        text_boxes.put("2_position_label", new TextContainer(SnapPosition.TOP_LEFT,
            "- pos 2:  ", 100, 350, .75f));

        text_boxes.put("1_position", new TextContainer(SnapPosition.TOP_LEFT,
            "0", 350, 300, .75f));

        text_boxes.put("2_position", new TextContainer(SnapPosition.TOP_LEFT,
            "0", 350, 350, .75f));

    }


    private boolean process_hud_event(Map<String, TextContainer> text_boxes, Event next_event)
    {
        if (next_event.type() == CoreEvent.WINDOW_RESIZE)
        {
            return true;
        }
        if (next_event instanceof PositionEvent(var type, var position))
        {
            if (type == DebugEvent.CAMERA_POSITION) {
                var current = text_boxes.get("1_position");
                var next = new TextContainer(current.snap(), position, current.x(), current.y(), current.scale());
                text_boxes.put("1_position", next);
            }
            if (type == DebugEvent.PLAYER_POSITION)
            {
                var current = text_boxes.get("2_position");
                var next = new TextContainer(current.snap(), position, current.x(), current.y(), current.scale());
                text_boxes.put("2_position", next);
            }
            return true;
        }
        if (next_event instanceof MessageEvent(var type, var message))
        {
            if (Objects.requireNonNull(type) == CoreEvent.FPS)
            {
                var current = text_boxes.get("fps");
                var next = new TextContainer(current.snap(), message, current.x(), current.y(), current.scale());
                text_boxes.put("fps", next);
                return true;
            }
        }
        if (next_event instanceof ViewDebugEvent(var type, float value))
        {
            switch (type)
            {
                case VIEW_PITCH ->
                {
                    var current_pitch = text_boxes.get("pitch");
                    var next_pitch = new TextContainer(current_pitch.snap(), String.valueOf(Math.toDegrees(value)), current_pitch.x(), current_pitch.y(), current_pitch.scale());
                    text_boxes.put("pitch", next_pitch);
                }

                case VIEW_YAW ->
                {
                    var current_yaw = text_boxes.get("yaw");
                    var next_yaw = new TextContainer(current_yaw.snap(), String.valueOf(Math.toDegrees(value)), current_yaw.x(), current_yaw.y(), current_yaw.scale());
                    text_boxes.put("yaw", next_yaw);
                }

                case VIEW_DIST ->
                {
                    var current_dist = text_boxes.get("dist");
                    var next_dist = new TextContainer(current_dist.snap(), String.valueOf(value), current_dist.x(), current_dist.y(), current_dist.scale());
                    text_boxes.put("dist", next_dist);
                }
            }

            return true;
        }
        return false;
    }

    @Override
    public void destroy()
    {
        // todo: game save or something
        resources.release_all();
    }
}
