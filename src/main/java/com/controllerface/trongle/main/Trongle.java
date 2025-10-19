package com.controllerface.trongle.main;

import com.juncture.alloy.camera.WorldCamera;
import com.juncture.alloy.ecs.GameMode;
import com.juncture.alloy.events.Event;
import com.juncture.alloy.game.GameContext;
import com.juncture.alloy.models.ModelRegistry;
import com.controllerface.trongle.components.Component;
import com.controllerface.trongle.events.GameEvent;
import com.controllerface.trongle.events.ModeSwitchEvent;
import com.controllerface.trongle.systems.camera.UniformViewSystem;
import com.controllerface.trongle.systems.input.InputSystem;

import java.util.logging.Logger;

public class Trongle extends GameContext<Component>
{
    private static final Logger LOGGER = Logger.getLogger(Trongle.class.getName());

    private final GameMode<Component> main_menu;
    private final GameMode<Component> base_game;

    public Trongle()
    {
        super(Component.class, "The Return of Trongle - Prototype");

        ecs.set_global(Component.Events, event_bus);
        ecs.set_global(Component.MainWindow, window);
        ecs.set_global(Component.MainCamera, new WorldCamera(window, event_bus));
        ecs.set_global(Component.Models, new ModelRegistry<>(GLTFModel.class, "/models/"));

        ecs.register_system(new InputSystem(ecs));
        ecs.register_system(new UniformViewSystem(ecs));

        event_bus.register(event_queue, GameEvent.MODE_SWiTCH);

        base_game = new BaseGame(ecs, gl_controller());
        main_menu = new MainMenu(ecs);

        main_menu.init();
        base_game.init();

        current_mode = main_menu;
    }

    private void process_events()
    {
        Event next_event;
        while ((next_event = event_queue.poll()) != null)
        {
            if (next_event instanceof ModeSwitchEvent)
            {
                current_mode.deactivate();
                if (current_mode == main_menu)
                {
                    current_mode = base_game;
                }
                else
                {
                    current_mode = main_menu;
                }
                current_mode.activate();
            }
        }
    }

    @Override
    public void loop()
    {
        LOGGER.fine("Starting Game loop");
        enter_loop();

        while (window.should_update() && dt < MAX_DT)
        {
            tick();
            process_events();
            frame_complete();
        }

        LOGGER.fine("Exiting Game loop");
        exit_loop();
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
        main_menu.destroy();
        base_game.destroy();
    }
}
