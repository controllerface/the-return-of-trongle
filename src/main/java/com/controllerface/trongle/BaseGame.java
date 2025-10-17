package com.controllerface.trongle;

import com.controllerface.trongle.render.GLTFModel;
import com.controllerface.trongle.render.RenderingSystem;
import com.juncture.alloy.data.*;
import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.ecs.GameMode;
import com.juncture.alloy.gpu.gl.GL_GraphicsController;
import com.juncture.alloy.utils.math.Bounds3f;
import com.juncture.alloy.utils.math.MathEX;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class BaseGame extends GameMode<Component>
{
    private final GL_GraphicsController gl_controller;

    private float sun_angle = 0.0f;
    private float day_speed = 0.1f;

    private final Vector3f sun_direction   = new Vector3f(0.0f, -1.0f, 0.0f);
    private final Vector3f moon_direction  = new Vector3f(0.0f, 1.0f, 0.0f);
    private final MutableFloat time_of_day = new MutableFloat(0.0f);

    public BaseGame(ECS<Component> ecs, GL_GraphicsController gl_controller)
    {
        super(ecs);
        this.gl_controller = gl_controller;
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

    private String player_entity;

    private void init_player()
    {
        player_entity = ecs.new_entity();

        float scale = 0.5f;
        var mass = 100f;
        var drag = 1f;
        var max_thrust = 10_000f;
        var max_yaw = 100f;
        var inertia = 100f;

        var max_speed = max_thrust / (mass * drag);
        var max_ang_speed = max_yaw / (inertia * drag);

        ecs.set_component(player_entity, Component.Player, Marker.MARKED);


//        ecs.set_component(player_entity, Component.Behavior, EntityBehavior.PLAYER);
//        ecs.set_component(player_entity, Component.Heading, new Vector3d());
//        ecs.set_component(player_entity, Component.FireCooldown, new MutableFloat(0));
//        ecs.set_component(player_entity, Component.FireRate, new MutableFloat(0.05f));
//        ecs.set_component(player_entity, Component.MaxPitch, new MutableFloat(20.0f));
//        ecs.set_component(player_entity, Component.MaxRoll, new MutableFloat(25.0f));
//        ecs.set_component(player_entity, Component.MaxSpeed, new MutableFloat(max_speed));
//        ecs.set_component(player_entity, Component.MaxAngSpeed, new MutableFloat(max_ang_speed));
//        ecs.set_component(player_entity, Component.TurretIndex, new MutableInt(0));
//        ecs.set_component(player_entity, Component.WeaponAccuracy, new MutableDouble(.85));
//        ecs.set_component(player_entity, Component.WeaponSpread, new MutableDouble(.05));
//        ecs.set_component(player_entity, Component.WeaponDamage, new MutableFloat(10.0f));
//        ecs.set_component(player_entity, Component.WeaponRange, new MutableFloat(600.0f));


//        var model_lights = init_lights(ecs, GLTFModel.TEST_CUBE, scale);
//        var model_turrets = init_turrets(ecs, GLTFModel.TEST_CUBE);
        ecs.set_component(player_entity, Component.Model, GLTFModel.TEST_CUBE);
//        ecs.set_component(player_entity, Component.ModelLights, model_lights);
//        ecs.set_component(player_entity, Component.Turrets, model_turrets);
        ecs.set_component(player_entity, Component.RenderBounds, new Bounds3f(player_entity));
        ecs.set_component(player_entity, Component.RenderPosition, new Vector3f(0.0f));
        ecs.set_component(player_entity, Component.RenderScale, new Vector3f(0.0f));
        ecs.set_component(player_entity, Component.RenderRotation, new Vector3f(0.0f));
        ecs.set_component(player_entity, Component.Transform, new Matrix4f());

//        Archetypes.physics(ecs, player_entity, mass, inertia, drag, max_thrust, max_yaw,
//            new Vector3d(0.0), new Vector3d(0.0), new Vector3d(scale));
    }


    @Override
    public void init()
    {
        init_player();

        ecs.set_global(Component.TimeOfDay, time_of_day);
        ecs.set_global(Component.PointLightCount, new MutableInt(0));
        ecs.set_global(Component.SpotLightCount, new MutableInt(0));
        ecs.set_global(Component.SimulationRemainder, new MutableDouble(0.0f));

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

        systems.add(new TransformUpdateSystem(ecs));
        systems.add(new CameraSystem(ecs));
        systems.add(new LightSpaceSystem(ecs));
        systems.add(new RenderingSystem(ecs, gl_controller));
    }

    @Override
    public void update(double dt)
    {
        cycle_day_night(dt);
    }

    @Override
    public void destroy()
    {

    }
}
