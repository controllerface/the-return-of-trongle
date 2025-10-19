package com.controllerface.trongle.events;

import com.juncture.alloy.events.Event;
import com.controllerface.trongle.components.EnemyType;

public record EnemyCountEvent(GameEvent type, EnemyType enemy_type, int count) implements Event
{
    public static EnemyCountEvent new_count(EnemyType enemy_type, int count)
    {
        return new EnemyCountEvent(GameEvent.ENEMY_COUNT_CHANGED, enemy_type, count);
    }
}
