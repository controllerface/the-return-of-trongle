package com.controllerface.trongle.main;

import com.juncture.alloy.game.GameConfig;

public class Main
{
    void main()
    {
        GameConfig.setup();
        new Trongle().run();
    }
}
