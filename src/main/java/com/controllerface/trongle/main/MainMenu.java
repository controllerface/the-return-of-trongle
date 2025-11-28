package com.controllerface.trongle.main;

import com.controllerface.trongle.events.ModeSwitchEvent;
import com.controllerface.trongle.input.InputBinding;
import com.controllerface.trongle.input.InputState;
import com.controllerface.trongle.menu.MenuRenderSystem;
import com.juncture.alloy.ecs.BaseComponent;
import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.ecs.ECSWorld;
import com.juncture.alloy.ecs.GameMode;
import com.juncture.alloy.events.EventBus;

public class MainMenu extends GameMode
{
    private InputState input_state;
    private EventBus event_bus;

    boolean latched = false;
    boolean first_load = true;

    private final ECSLayer<BaseComponent> base_layer;

    public MainMenu(ECSWorld world)
    {
        super(world);
        this.base_layer = world.get(BaseComponent.class);
    }

    private MenuRenderSystem rendering_system;

    @Override
    public void init()
    {
        input_state = BaseComponent.Input.global(base_layer);
        event_bus = world.event_bus;
        rendering_system = new MenuRenderSystem(world);
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
        world.register_system(rendering_system);
    }

    @Override
    public void deactivate()
    {
        world.deregister_system(rendering_system);
    }

    @Override
    public void destroy()
    {

    }
}
