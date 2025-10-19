package com.controllerface.trongle.main;

import com.juncture.alloy.data.*;
import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.ecs.GameMode;
import com.juncture.alloy.events.Event;
import com.juncture.alloy.events.EventBus;
import com.juncture.alloy.gpu.gl.GL_GraphicsController;
import com.juncture.alloy.utils.math.MathEX;
import com.juncture.alloy.utils.math.Ray3d;
import com.controllerface.trongle.components.*;
import com.controllerface.trongle.events.EnemyCountEvent;
import com.controllerface.trongle.events.EntityDestoryEvent;
import com.controllerface.trongle.events.GameEvent;
import com.controllerface.trongle.events.ModeSwitchEvent;
import com.controllerface.trongle.systems.behavior.AxisDirection;
import com.controllerface.trongle.systems.behavior.EntityBehavior;
import com.controllerface.trongle.systems.behavior.EntityBehaviorSystem;
import com.controllerface.trongle.systems.behavior.behaviors.MovementDirection;
import com.controllerface.trongle.systems.camera.CameraSystem;
import com.controllerface.trongle.systems.camera.LightSpaceSystem;
import com.controllerface.trongle.systems.input.InputBinding;
import com.controllerface.trongle.systems.input.InputState;
import com.controllerface.trongle.systems.physics.PhysicsSystem;
import com.controllerface.trongle.systems.physics.TransformUpdateSystem;
import com.controllerface.trongle.systems.physics.UpkeepSystem;
import com.controllerface.trongle.systems.rendering.DebugRenderingSystem;
import com.controllerface.trongle.systems.rendering.ParticleSystem;
import com.controllerface.trongle.systems.rendering.RenderingSystem;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class BaseGame extends GameMode<Component>
{
    private static final boolean DEBUG_MODE = false;

    private static final int MAX_NPC_TURN_TIME_SECONDS = 5;

    private final Queue<Event> event_queue = new LinkedBlockingQueue<>();

    private final Duration DAY_LENGTH = Duration.ofMinutes(15);

    private final Vector3f sun_direction   = new Vector3f(0.0f, -1.0f, 0.0f);
    private final Vector3f moon_direction  = new Vector3f(0.0f, 1.0f, 0.0f);
    private final MutableFloat time_of_day = new MutableFloat(0.0f);

    private float sun_angle = 0.0f;
    private float day_speed = 0.1f;

    private String player_entity;

    private InputState input_state;
    private EventBus event_bus;
    boolean latched = false;

    private final MutableFloat time_index = new MutableFloat(0.0f);

    private final Map<String, EnemyType> types = new HashMap<>();
    private final Set<String> drones = new HashSet<>();
    private final Set<String> ufos   = new HashSet<>();
    private final GL_GraphicsController gl_controller;

    public BaseGame(ECS<Component> ecs, GL_GraphicsController glController)
    {
        super(ecs);
        gl_controller = glController;
    }

    private void init_world()
    {
        var mouse_ray = Ray3d.generate_empty(player_entity);
        var mouse_ray_entity = ecs.new_entity();
        Archetypes.mouse_target(ecs, mouse_ray_entity, mouse_ray);

        ecs.set_global(Component.MouseRay, mouse_ray_entity);

        ecs.set_global(Component.TimeOfDay, time_of_day);
        ecs.set_global(Component.PointLightCount, new MutableInt(0));
        ecs.set_global(Component.SpotLightCount, new MutableInt(0));
        ecs.set_global(Component.SimulationRemainder, new MutableDouble(0.0f));

        day_speed = calculate_day_speed(DAY_LENGTH);

        var sun_light_entity = ecs.new_entity();
        ecs.set_component(sun_light_entity, Component.SunLight, Marker.MARKED);
        ecs.set_component(sun_light_entity, Component.Color, new Vector4f(0.93f, 0.90f, 0.71f, 1.0f));
        ecs.set_component(sun_light_entity, Component.Direction, sun_direction);
        ecs.set_component(sun_light_entity, Component.LightIntensity, new LightIntensity(0.005f, 0.5f));

        var moon_light_entity = ecs.new_entity();
        ecs.set_component(moon_light_entity, Component.MoonLight, Marker.MARKED);
        ecs.set_component(moon_light_entity, Component.Color, new Vector4f(0.04f, 0.07f, 0.32f, 1.0f));
        ecs.set_component(moon_light_entity, Component.Direction, moon_direction);
        ecs.set_component(moon_light_entity, Component.LightIntensity, new LightIntensity(0.007f, 0.2f));
    }

    private void init_systems()
    {
        systems.add(new EntityBehaviorSystem(ecs));
        systems.add(new PhysicsSystem(ecs));
        systems.add(new ParticleSystem(ecs));
        systems.add(new TransformUpdateSystem(ecs));
        systems.add(new CameraSystem(ecs));
        systems.add(new LightSpaceSystem(ecs));
        systems.add(new RenderingSystem(ecs, gl_controller));
        systems.add(new UpkeepSystem(ecs));

        // for debugging todo: move below code to a proper debug start up process
        if (DEBUG_MODE) systems.add(new DebugRenderingSystem(ecs));
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

    @Override
    public void update(double dt)
    {
        Event next_event;
        boolean need_update = false;
        while ((next_event = event_queue.poll()) != null)
        {
            if (next_event instanceof EntityDestoryEvent destroyEvent)
            {
                var type = types.remove(destroyEvent.entity_id());
                switch (type)
                {
                    case DRONE ->
                    {
                        if (drones.remove(destroyEvent.entity_id()))
                        {
                            need_update = true;
                        }
                    }
                    case UFO ->
                    {
                        if (ufos.remove(destroyEvent.entity_id()))
                        {
                            need_update = true;
                        }
                    }

                    // todo: add more enemy types
                    case GENERAL -> {}
                    case TANKER -> {}
                    case null -> {}
                    default -> {}
                }
            }
        }

        if (need_update)
        {
            event_bus.emit_event(EnemyCountEvent.new_count(EnemyType.DRONE, drones.size()));
            event_bus.emit_event(EnemyCountEvent.new_count(EnemyType.UFO, ufos.size()));
        }

        time_index.value += (float) dt;
        process_key_state();
        cycle_day_night(dt);
    }

    @Override
    public void activate()
    {
        super.activate();
        latched = true;
    }

    @Override
    public void init()
    {
        ecs.set_global(Component.TimeIndex, time_index);

        input_state = Component.Input.global(ecs);
        event_bus = Component.Events.global(ecs);

        event_bus.register(event_queue, GameEvent.ENTITY_DESTROYED);

        init_world();
        init_player();
        init_systems();
    }

    @Override
    public void destroy()
    {
        // todo: game save or something
    }

    private void init_player()
    {
        player_entity = ecs.new_entity();

        float scale = 1f;
        var mass = 1f;
        var drag = 1f;
        var max_thrust = 1f;
        var max_yaw = 10f;
        var inertia = 1f;

        var max_speed = max_thrust / (mass * drag);
        var max_ang_speed = max_yaw / (inertia * drag);

        ecs.set_component(player_entity, Component.Player, Marker.MARKED);
        ecs.set_component(player_entity, Component.Behavior, EntityBehavior.PLAYER);
        ecs.set_component(player_entity, Component.Heading, new Vector3d());
        ecs.set_component(player_entity, Component.MaxPitch, new MutableFloat(20.0f));
        ecs.set_component(player_entity, Component.MaxRoll, new MutableFloat(25.0f));
        ecs.set_component(player_entity, Component.MaxSpeed, new MutableFloat(max_speed));
        ecs.set_component(player_entity, Component.MaxAngSpeed, new MutableFloat(max_ang_speed));

        Archetypes.model(ecs, player_entity, GLTFModel.TEST_CUBE, scale);
        Archetypes.physics(ecs, player_entity, mass, inertia, drag, max_thrust, max_yaw,
            new Vector3d(0.0), new Vector3d(0f, -(Math.PI / 2), 0f), new Vector3d(scale));
    }
}
