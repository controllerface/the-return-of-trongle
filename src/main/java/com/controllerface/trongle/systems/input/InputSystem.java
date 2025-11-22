package com.controllerface.trongle.systems.input;

import com.juncture.alloy.ecs.ECSLayer;
import com.juncture.alloy.ecs.ECSSystem;
import com.controllerface.trongle.components.Component;
import com.juncture.alloy.ecs.ECSWorld;

import static org.lwjgl.glfw.GLFW.*;

public class InputSystem extends ECSSystem
{
    private final InputState input_state;

    private final ECSLayer<Component> ecs;

    public InputSystem(ECSWorld world)
    {
        super(world);
        ecs = world.get(Component.class);
        input_state = new InputState();
        ecs.set_global(Component.Input, input_state);
        init_callbacks();
    }

    private void init_callbacks()
    {
        var glfw_window = glfwGetCurrentContext();
        try (var cursor_cb = glfwSetCursorPosCallback(glfw_window, this::mousePosCallback);
             var button_cb = glfwSetMouseButtonCallback(glfw_window, this::mouseButtonCallback);
             var scroll_cb = glfwSetScrollCallback(glfw_window, this::mouseScrollCallback);
             var key_cb = glfwSetKeyCallback(glfw_window, this::keyCallback))
        {
            assert cursor_cb == null;
            assert button_cb == null;
            assert scroll_cb == null;
            assert key_cb == null;
        }
    }

    private void keyCallback(long window, int key, int scancode, int action, int mods)
    {
        if (key == GLFW_KEY_UNKNOWN)
        {
            return;
        }

        if (action == GLFW_PRESS)
        {
            input_state.key_press(key);
        }
        else if (action == GLFW_RELEASE)
        {
            input_state.key_release(key);
        }
    }

    private void mouseScrollCallback(long window, double xOffset, double yOffset)
    {
        input_state.mouse_scroll(yOffset);
    }

    private void mouseButtonCallback(long window, int button, int action, int mods)
    {
        if (action == GLFW_PRESS)
        {
            input_state.signal_mouse_down();
            input_state.mouse_press(button);
        }
        else if (action == GLFW_RELEASE)
        {
            input_state.signal_mouse_up();
            input_state.mouse_release(button);
        }
    }

    private void mousePosCallback(long window, double xpos, double ypos)
    {
        input_state.mouse_move(xpos, ypos);
    }

    @Override
    public void tick(double dt) { }
}
