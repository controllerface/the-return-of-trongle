package com.controllerface.trongle.components;

import com.juncture.alloy.data.*;
import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.ecs.ECSComponent;
import com.juncture.alloy.events.EventBus;
import com.juncture.alloy.utils.math.*;
import com.controllerface.trongle.systems.behavior.AxisDirection;
import com.controllerface.trongle.systems.behavior.EntityBehavior;
import com.controllerface.trongle.systems.behavior.behaviors.MovementDirection;
import com.controllerface.trongle.systems.input.InputState;
import org.joml.Vector3f;

public enum Component implements ECSComponent<Component>
{
    // Behaviors
    Behavior            (EntityBehavior.class),
    CurrentDirection    (MovementDirection.class),
    CurrentThrustAxis   (AxisDirection.class),
    CurrentYawAxis      (AxisDirection.class),
    CurrentTurnTime     (MutableDouble.class),
    TurnTime            (MutableDouble.class),

    // Player
    Player              (Marker.class),
    Input               (InputState.class),

    // Bookkeeping
    Destructible        (Marker.class),
    HitScanWeapon       (Marker.class),
    Projectile          (Marker.class),
    Integrity           (MutableFloat.class),
    Lifetime            (MutableDouble.class),
    MaxLifetime         (MutableDouble.class),
    ProjectileDamage    (MutableFloat.class),
    TimeIndex           (MutableFloat.class),

    // Particles
    Explosion           (Marker.class),
    Particle            (Marker.class),
    ParticleVelocity    (Vector3f.class),
    ParticleGravity     (Vector3f.class),
    ParticleColor       (Vector3f.class),
    TrailTipColor       (Vector3f.class),
    TrailTailColor      (Vector3f.class),

    // Lights
    SunLight            (Marker.class),
    MoonLight           (Marker.class),
    TimeOfDay           (MutableFloat.class),

    // Events
    Events              (EventBus.class),

    ;

    private enum SubType
    {
        NORMAL,
        GROUP,
    }

    private final Class<?> data_class;
    private final SubType sub_type;

    Component(Class<?> data_class, SubType sub_type)
    {
        this.data_class = data_class;
        this.sub_type = sub_type;
        assert sub_type != SubType.GROUP || data_class == String[].class;
    }

    Component(Class<?> data_class)
    {
        this.data_class = data_class;
        this.sub_type = SubType.NORMAL;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T coerce(Object component_object)
    {
        assert component_object != null : "Attempted to coerce null component";
        assert type_check(component_object) : "Attempted to coerce incompatible component";
        return (T) data_class.cast(component_object);
    }

    @Override
    public <T> T for_entity(ECSLayer<Component> ecs, String entity)
    {
        var component_object = ecs.get_component_for(entity, this);
        assert component_object != null : "entity: " + entity + " has no: " + this + " component";
        return coerce(component_object);
    }

    @Override
    public <T> T for_entity_or_null(ECSLayer<Component> ecs, String entity)
    {
        var component_object = ecs.get_component_for(entity, this);
        return component_object == null ? null : coerce(component_object);
    }

    @Override
    public <T> T global(ECSLayer<Component> ecs)
    {
        return for_entity(ecs, ECSLayer.GLOBAL_ENTITY);
    }

    @Override
    public <T> T global_or_null(ECSLayer<Component> ecs)
    {
        return for_entity_or_null(ecs, ECSLayer.GLOBAL_ENTITY);
    }

    @Override
    public boolean type_check(Object component_object)
    {
        return data_class.isInstance(component_object);
    }

    @Override
    public boolean is_group()
    {
        return sub_type == SubType.GROUP;
    }
}
