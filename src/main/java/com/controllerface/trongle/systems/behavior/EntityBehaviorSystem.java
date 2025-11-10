package com.controllerface.trongle.systems.behavior;

import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.ecs.ECSSystem;
import com.controllerface.trongle.components.Component;
import com.controllerface.trongle.systems.behavior.behaviors.PlayerBehavior;

public class EntityBehaviorSystem extends ECSSystem<Component>
{
    public EntityBehaviorSystem(ECSLayer<Component> ecs)
    {
        super(ecs);
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
                case PLAYER -> PlayerBehavior.behave(dt, ecs, entity_id);
            }
        }
    }
}
