package com.controllerface.trongle.main;

import com.juncture.alloy.ecs.ECS;
import com.juncture.alloy.ecs.GameMode;
import com.juncture.alloy.events.EventBus;
import com.controllerface.trongle.components.Component;
import com.controllerface.trongle.events.ModeSwitchEvent;
import com.controllerface.trongle.systems.input.InputBinding;
import com.controllerface.trongle.systems.input.InputState;
import com.controllerface.trongle.systems.rendering.MenuRenderSystem;

public class MainMenu extends GameMode
{
    private InputState input_state;
    private EventBus event_bus;

    boolean latched = false;
    boolean first_load = true;

    private final ECS<Component> ecs;

    public MainMenu(ECS<Component> _ecs)
    {
        this.ecs = _ecs;
    }

    private MenuRenderSystem rendering_system;

    @Override
    public void init()
    {
        input_state = Component.Input.global(ecs);
        event_bus = Component.Events.global(ecs);
        rendering_system = new MenuRenderSystem(ecs);
    }

    @Override
    public void update(double dt)
    {
        boolean escape = input_state.is_active(InputBinding.ESCAPE);
        if (escape && !latched)
        {
            latched = true;
            event_bus.emit_event(new ModeSwitchEvent());
        }
        else if (!escape)
        {
            latched = false;
        }
    }

    @Override
    public void activate()
    {
        if (!first_load) rendering_system.capture_screen();
        if (first_load) first_load = false;
        latched = true;
        ecs.register_system(rendering_system);
    }

    @Override
    public void deactivate()
    {
        ecs.deregister_system(rendering_system);
    }

    @Override
    public void destroy()
    {

    }
}
