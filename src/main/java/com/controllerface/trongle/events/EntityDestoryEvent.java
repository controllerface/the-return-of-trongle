package com.controllerface.trongle.events;

import com.juncture.alloy.events.Event;

public record EntityDestoryEvent(GameEvent type, String entity_id) implements Event
{
    public static EntityDestoryEvent destroy(String entity_id)
    {
        return new EntityDestoryEvent(GameEvent.ENTITY_DESTROYED, entity_id);
    }
}
