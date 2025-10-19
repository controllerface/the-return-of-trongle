package com.controllerface.trongle.systems.behavior.behaviors;

import com.controllerface.trongle.main.Global;

import java.util.List;

public enum MovementDirection
{
    FORWARD,
    FORWARD_LEFT,
    FORWARD_RIGHT,
    BACKWARD,
    BACKWARD_LEFT,
    BACKWARD_RIGHT,
    LEFT,
    RIGHT,

    ;

    private static final List<MovementDirection> VALUES = List.of(values());
    private static final int COUNT = VALUES.size();

    public static MovementDirection random()
    {
        return VALUES.get(Global.random.nextInt(COUNT));
    }
}
