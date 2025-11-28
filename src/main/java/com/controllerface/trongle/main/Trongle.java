package com.controllerface.trongle.main;

import com.controllerface.trongle.events.GameEvent;
import com.controllerface.trongle.events.ModeSwitchEvent;
import com.controllerface.trongle.input.InputState;
import com.juncture.alloy.camera.WorldCamera;
import com.juncture.alloy.data.MutableDouble;
import com.juncture.alloy.data.MutableInt;
import com.juncture.alloy.ecs.BaseComponent;
import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.ecs.GameMode;
import com.juncture.alloy.events.Event;
import com.juncture.alloy.game.GameContext;
import com.juncture.alloy.input.InputSystem;
import com.juncture.alloy.models.ModelRegistry;
import com.juncture.alloy.physics.PhysicsComponent;
import com.juncture.alloy.rendering.RenderComponent;
import com.juncture.alloy.rendering.camera.UniformViewSystem;

import java.util.logging.Logger;

public class Trongle extends GameContext
{
    private static final String WINDOW_TITLE = "The Return of Trongle - Prototype";

    private static final Logger LOGGER = Logger.getLogger(Trongle.class.getName());

    private final GameMode main_menu;
    private final GameMode base_game;

    public Trongle()
    {
        super(WINDOW_TITLE);

        var base_layer = new ECSLayer<>(BaseComponent.class);
        var rend_layer = new ECSLayer<>(RenderComponent.class);
        var phys_layer = new ECSLayer<>(PhysicsComponent.class);

        world.register(BaseComponent.class, base_layer);
        world.register(RenderComponent.class, rend_layer);
        world.register(PhysicsComponent.class, phys_layer);

        rend_layer.set_global(RenderComponent.MainWindow, window);
        rend_layer.set_global(RenderComponent.MainCamera, new WorldCamera(window, event_bus));
        rend_layer.set_global(RenderComponent.Models, new ModelRegistry(GLTFModel.class, "/models/"));
        rend_layer.set_global(RenderComponent.PointLightCount, new MutableInt(0));
        rend_layer.set_global(RenderComponent.SpotLightCount, new MutableInt(0));

        phys_layer.set_global(PhysicsComponent.SimulationRemainder, new MutableDouble(0.0f));

        var input_state = new InputState();
        base_layer.set_global(BaseComponent.Input, input_state);

        world.register_system(new InputSystem<>(world, input_state));
        world.register_system(new UniformViewSystem(world));

        event_bus.register(event_queue, GameEvent.MODE_SWiTCH);

        base_game = new BaseGame(world, gl_controller());
        main_menu = new MainMenu(world);

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
