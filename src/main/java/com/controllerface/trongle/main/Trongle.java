package com.controllerface.trongle.main;

import com.juncture.alloy.camera.WorldCamera;
import com.juncture.alloy.ecs.ECSLayer;
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

public class Trongle extends GameContext
{
    private static final Logger LOGGER = Logger.getLogger(Trongle.class.getName());

    private final GameMode main_menu;
    private final GameMode base_game;

    private final ECSLayer<Component> ecs1;

    public Trongle()
    {
        super("The Return of Trongle - Prototype", Component.class);

        ecs1 = new ECSLayer<>(Component.class);
        ecs.register(Component.class, ecs1);

        ecs1.set_global(Component.Events, event_bus);
        ecs1.set_global(Component.MainWindow, window);
        ecs1.set_global(Component.MainCamera, new WorldCamera(window, event_bus));
        ecs1.set_global(Component.Models, new ModelRegistry<>(GLTFModel.class, "/models/"));

        ecs1.register_system(new InputSystem(ecs1));
        ecs1.register_system(new UniformViewSystem(ecs1));

        event_bus.register(event_queue, GameEvent.MODE_SWiTCH);

        base_game = new BaseGame(ecs1, gl_controller());
        main_menu = new MainMenu(ecs1);

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
