package com.controllerface.trongle.main;

import com.juncture.alloy.game.GameConfig;

import java.util.logging.Logger;

public class Main
{
    //private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    static void main()
    {
        GameConfig.setup();

        //LOGGER.info("Starting up");

        new Trongle().run();

        //LOGGER.info("Shutting down");
    }
}
