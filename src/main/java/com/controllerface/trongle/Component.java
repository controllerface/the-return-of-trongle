package com.controllerface.trongle;

import com.controllerface.trongle.render.GLTFModel;
import com.juncture.alloy.camera.WorldCamera;
import com.juncture.alloy.data.*;
import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.ecs.ECSComponent;
import com.juncture.alloy.events.EventBus;
import com.juncture.alloy.gpu.RenderSet;
import com.juncture.alloy.gpu.Window;
import com.juncture.alloy.models.ModelRegistry;
import com.juncture.alloy.physics.bvh.RenderTree;
import com.juncture.alloy.utils.math.Bounds3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

public enum Component implements ECSComponent<Component>
{
    MainWindow          (Window.class),
    Events              (EventBus.class),

    Turrets             (String[].class, SubType.GROUP),

    // Player
    Player              (Marker.class),

    // Camera
    MainCamera          (WorldCamera.class),
    CameraPitch         (MutableFloat.class),
    CameraYaw           (MutableFloat.class),
    CameraZoom          (MutableFloat.class),

    // Models
    Model               (GLTFModel.class),
    Models              (ModelRegistry.class),

    // Rendering
    RenderPosition      (Vector3f.class),
    RenderScale         (Vector3f.class),
    RenderRotation      (Vector3f.class),
    RenderVisible       (RenderSet.class),
    RenderBounds        (Bounds3f.class),
    RenderBVH           (RenderTree.class),
    Transform           (Matrix4f.class),

    // Lighting
    SunLight            (Marker.class),
    MoonLight           (Marker.class),
    Light               (LightEmitterType.class),
    Color               (Vector4f.class),
    Direction           (Vector3f.class),
    LightRange          (MutableFloat.class),
    LightIntensity      (LightIntensity.class),
    InnerCone           (MutableFloat.class),
    OuterCone           (MutableFloat.class),
    PointLightCount     (MutableInt.class),
    SpotLightCount      (MutableInt.class),
    TimeOfDay           (MutableFloat.class),
    LightSpaceMatrix    (Matrix4f.class),
    ModelLights         (String[].class, SubType.GROUP),


    // Physics
    PhysicsTracked      (Marker.class),
    SimulationRemainder (MutableDouble.class),
    Position            (Vector3d.class),
    Rotation            (Vector3d.class),
    Scale               (Vector3d.class),
    PreviousPosition    (Vector3d.class),
    PreviousRotation    (Vector3d.class),

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
