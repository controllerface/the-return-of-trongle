package com.controllerface.trongle.systems;

import com.juncture.alloy.ecs.ECSSystem;
import com.juncture.alloy.ecs.ECSWorld;

public class UpkeepSystem extends ECSSystem
{

    public UpkeepSystem(ECSWorld world)
    {
        super(world);
    }

    @Override
    public void tick(double dt)
    {
    }
}
