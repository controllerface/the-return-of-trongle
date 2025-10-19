package com.controllerface.trongle.components;

import com.juncture.alloy.camera.WorldCamera;
import com.juncture.alloy.data.*;
import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.ecs.ECSComponent;
import com.juncture.alloy.events.EventBus;
import com.juncture.alloy.gpu.RenderSet;
import com.juncture.alloy.gpu.Window;
import com.juncture.alloy.models.ModelRegistry;
import com.juncture.alloy.physics.bvh.PhysicsTree;
import com.juncture.alloy.physics.bvh.RenderTree;
import com.juncture.alloy.utils.math.*;
import com.controllerface.trongle.main.GLTFModel;
import com.controllerface.trongle.systems.behavior.AxisDirection;
import com.controllerface.trongle.systems.behavior.EntityBehavior;
import com.controllerface.trongle.systems.behavior.behaviors.MovementDirection;
import com.controllerface.trongle.systems.input.InputState;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

public enum Component implements ECSComponent<Component>
{
    // Behaviors
    Behavior            (EntityBehavior.class),
    MaxPitch            (MutableFloat.class),
    MaxRoll             (MutableFloat.class),
    MaxSpeed            (MutableFloat.class),
    MaxAngSpeed         (MutableFloat.class),
    CurrentDirection    (MovementDirection.class),
    CurrentThrustAxis   (AxisDirection.class),
    CurrentYawAxis      (AxisDirection.class),
    CurrentTurnTime     (MutableDouble.class),
    TurnTime            (MutableDouble.class),

    // Player
    Player              (Marker.class),
    Input               (InputState.class),
    MouseCollider       (Marker.class),

    // Bookkeeping
    Destructible        (Marker.class),
    HitScanWeapon       (Marker.class),
    Projectile          (Marker.class),
    BillboardSize       (MutableFloat.class),
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
    HitScanTrail        (Quad3d.class),
    HitScanTrailRender  (Quad3d.class),

    // Rendering
    Model               (GLTFModel.class),
    RenderBounds        (Bounds3f.class),
    RenderPosition      (Vector3f.class),
    RenderScale         (Vector3f.class),
    RenderRotation      (Vector3f.class),
    RenderOrigin        (Vector3f.class),
    RenderTerminus      (Vector3f.class),
    Transform           (Matrix4f.class),
    TerrainIndex        (Long.class),
    RenderBVH           (RenderTree.class),
    RenderVisible       (RenderSet.class),

    // Lights
    SunLight            (Marker.class),
    MoonLight           (Marker.class),
    TimeOfDay           (MutableFloat.class),
    Light               (LightEmitterType.class),
    LightIntensity      (LightIntensity.class),
    LightRange          (MutableFloat.class),
    InnerCone           (MutableFloat.class),
    OuterCone           (MutableFloat.class),
    Color               (Vector4f.class),
    Direction           (Vector3f.class),
    ModelLights         (String[].class, SubType.GROUP),
    PointLightCount     (MutableInt.class),
    SpotLightCount      (MutableInt.class),
    LightSpaceMatrix    (Matrix4f.class),

    // Physics
    PhysicsTracked      (Marker.class),
    CollisionBVH        (PhysicsTree.class),
    SimulationRemainder (MutableDouble.class),
    Bounds              (Bounds3d.class),
    Hulls               (MutableConvexHull[].class),
    Acceleration        (Vector3d.class),
    AngularAcceleration (MutableFloat.class),
    Velocity            (Vector3d.class),
    AngularVelocity     (MutableFloat.class),
    Position            (Vector3d.class),
    Rotation            (Vector3d.class),
    Scale               (Vector3d.class),
    PreviousPosition    (Vector3d.class),
    PreviousRotation    (Vector3d.class),
    Heading             (Vector3d.class),
    Mass                (MutableFloat.class),
    Inertia             (MutableFloat.class),
    MaxThrust           (MutableFloat.class),
    MaxYaw              (MutableFloat.class),
    Thrust              (MutableFloat.class),
    Yaw                 (MutableFloat.class),
    Drag                (MutableFloat.class),
    RayCast             (Ray3d.class),
    RayCastHit          (Vector3d.class),
    RayCastFound        (MutableBoolean.class),
    RayCastComplete     (MutableBoolean.class),
    RayCastInteract     (Boolean.class),
    HitScanResult       (String.class),

    // Camera
    MainCamera          (WorldCamera.class),
    CameraPitch         (MutableFloat.class),
    CameraYaw           (MutableFloat.class),
    CameraZoom          (MutableFloat.class),

    // Window
    MainWindow          (Window.class),

    // Models
    Models              (ModelRegistry.class),

    // Events
    Events              (EventBus.class),

    // Mouse Ray Object
    MouseRay            (String.class),

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
    public <T> T for_entity(ECS<Component> ecs, String entity)
    {
        var component_object = ecs.get_component_for(entity, this);
        assert component_object != null : "entity: " + entity + " has no: " + this + " component";
        return coerce(component_object);
    }

    @Override
    public <T> T for_entity_or_null(ECS<Component> ecs, String entity)
    {
        var component_object = ecs.get_component_for(entity, this);
        return component_object == null ? null : coerce(component_object);
    }

    @Override
    public <T> T global(ECS<Component> ecs)
    {
        return for_entity(ecs, ECS.GLOBAL_ENTITY);
    }

    @Override
    public <T> T global_or_null(ECS<Component> ecs)
    {
        return for_entity_or_null(ecs, ECS.GLOBAL_ENTITY);
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
