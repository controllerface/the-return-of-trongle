package com.controllerface.trongle.events;

import com.juncture.alloy.events.Event;

public record ModeSwitchEvent(GameEvent type) implements Event
{
    public ModeSwitchEvent()
    {
        this(GameEvent.MODE_SWiTCH);
    }
}
