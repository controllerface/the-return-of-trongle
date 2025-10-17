package com.controllerface.trongle;

import com.controllerface.trongle.render.GLTFModel;
import com.juncture.alloy.camera.WorldCamera;
import com.juncture.alloy.game.GameContext;
import com.juncture.alloy.models.ModelRegistry;

import java.util.logging.Logger;

public class Trongle extends GameContext<Component>
{
    private static final Logger LOGGER = Logger.getLogger(Trongle.class.getName());

    Trongle()
    {
        super(Component.class, "Return of the Trongle - Prototype");

        ecs.set_global(Component.Events, event_bus);
        ecs.set_global(Component.MainWindow, window);
        ecs.set_global(Component.MainCamera, new WorldCamera(window, event_bus));
        ecs.set_global(Component.Models, new ModelRegistry<>(GLTFModel.class, "/models/"));

        var base_game = new BaseGame(ecs, gl_controller());
        base_game.init();
        current_mode = base_game;
    }

    @Override
    public void loop()
    {
        LOGGER.fine("Entering Game loop");
        enter_loop();

        while (window.should_update() && dt < MAX_DT)
        {
            tick();
            frame_complete();
        }

        LOGGER.fine("Exiting Game loop");
        exit_loop();
    }
}
