package com.controllerface.trongle.behavior;

import com.juncture.alloy.behaviors.EntityBehavior;
import com.juncture.alloy.ecs.ECSWorld;

public enum TrongleEntityBehavior implements EntityBehavior
{
    PLAYER(new PlayerBehavior()),

    ;

    final EntityBehavior inner;

    TrongleEntityBehavior(EntityBehavior inner)
    {
        this.inner = inner;
    }

    @Override
    public void behave(double dt, ECSWorld world, String entity_id)
    {
        inner.behave(dt, world, entity_id);
    }
}
