package com.controllerface.trongle.systems.behavior;

import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.ecs.ECSSystem;
import com.controllerface.trongle.components.Component;
import com.juncture.alloy.ecs.ECSWorld;
import com.juncture.alloy.physics.PhysicsComponent;
import com.juncture.alloy.rendering.RenderComponent;

public class EntityBehaviorSystem extends ECSSystem
{
    private final ECSLayer<Component> ecs;
    private final ECSLayer<PhysicsComponent> pecs;
    private final ECSLayer<RenderComponent> recs;

    public EntityBehaviorSystem(ECSWorld world)
    {
        super(world);
        ecs = world.get(Component.class);
        pecs = world.get(PhysicsComponent.class);
        recs = world.get(RenderComponent.class);
    }

    @Override
    public void tick(double dt)
    {
        var behaviors = ecs.get_components(Component.Behavior);
        for (var behavior_entry : behaviors.entrySet())
        {
            var entity_id = behavior_entry.getKey();
            var behavior = Component.Behavior.<EntityBehavior>coerce(behavior_entry.getValue());
            switch (behavior)
            {
                case PLAYER -> PlayerBehavior.behave(dt, ecs, pecs, recs, entity_id);
            }
        }
    }
}
