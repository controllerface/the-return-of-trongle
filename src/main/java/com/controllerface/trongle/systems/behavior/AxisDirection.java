package com.controllerface.trongle.systems.behavior;

import com.controllerface.trongle.main.Global;

import java.util.List;

public enum AxisDirection
{
    POSITIVE,
    NEGATIVE,
    NEUTRAL,

    ;

    private static final List<AxisDirection> VALUES = List.of(values());
    private static final int COUNT = VALUES.size();

    public static AxisDirection random()
    {
        return VALUES.get(Global.random.nextInt(COUNT));
    }
}
