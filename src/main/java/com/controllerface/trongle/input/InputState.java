package com.controllerface.trongle.input;

import com.juncture.alloy.input.IInputState;
import org.joml.Vector2d;
import org.joml.Vector2dc;

import java.util.EnumMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

public class InputState implements IInputState<InputBinding>
{
    private final boolean[] keys          = new boolean[350];
    private final boolean[] mouse         = new boolean[9];

    private final Vector2d mouse_pos      = new Vector2d();
    private final Vector2d mouse_pos_last = new Vector2d();

    private double mouse_scroll = 0;
    private int mouse_count = 0;

    private static final Map<InputBinding, Integer> BINDINGS = new EnumMap<>(InputBinding.class);

    // todo: make this non-static and configurable
    static
    {
        for (var binding : InputBinding.values())
        {
            int input = switch (binding)
            {
                case ESCAPE          -> GLFW_KEY_ESCAPE;

                case MOVE_FORWARD    -> GLFW_KEY_W;
                case MOVE_BACKWARD   -> GLFW_KEY_S;
                case MOVE_LEFT       -> GLFW_KEY_A;
                case MOVE_RIGHT      -> GLFW_KEY_D;
                case ROTATE_LEFT     -> GLFW_KEY_Q;
                case ROTATE_RIGHT    -> GLFW_KEY_E;

                case PRIMARY_FIRE    -> GLFW_MOUSE_BUTTON_1;
                case SECONDARY_FIRE  -> GLFW_MOUSE_BUTTON_2;
                case CAMERA_ADJUST   -> GLFW_MOUSE_BUTTON_3;
                case MOUSE_BACK      -> GLFW_MOUSE_BUTTON_4;
                case MOUSE_FORWARD   -> GLFW_MOUSE_BUTTON_5;
            };
            BINDINGS.put(binding, input);
        }
    }

    @Override
    public void signal_mouse_down()
    {
        mouse_count++;
    }

    @Override
    public void signal_mouse_up()
    {
        mouse_count--;
    }

    @Override
    public boolean any_mouse_buttons_held()
    {
        return mouse_count > 0;
    }

    @Override
    public double get_scroll()
    {
        return mouse_scroll;
    }

    @Override
    public Vector2dc get_mouse_pos()
    {
        return mouse_pos;
    }

    @Override
    public void calculate_mouse_delta(Vector2d output)
    {
        float dx = (float) (mouse_pos.x - mouse_pos_last.x);
        float dy = (float) (mouse_pos.y - mouse_pos_last.y);
        output.set(dx, dy);
    }

    @Override
    public void mouse_pos_reset()
    {
        mouse_pos_last.set(mouse_pos);
    }

    @Override
    public void mouse_scroll_reset()
    {
        mouse_scroll = 0;
    }

    @Override
    public void mouse_move(double xpos, double ypos)
    {
        mouse_pos_last.set(mouse_pos);
        mouse_pos.set(xpos, ypos);
    }

    @Override
    public void mouse_scroll(double scroll_offset)
    {
        mouse_scroll = scroll_offset;
    }

    @Override
    public void mouse_press(int button)
    {
        if (button < mouse.length)
        {
            mouse[button] = true;
        }
    }

    @Override
    public void mouse_release(int button)
    {
        if (button < mouse.length)
        {
            mouse[button] = false;
        }
    }

    @Override
    public void key_press(int button)
    {
        if (button < keys.length)
        {
            keys[button] = true;
        }
    }

    @Override
    public void key_release(int button)
    {
        if (button < keys.length)
        {
            keys[button] = false;
        }
    }

    @Override
    public boolean is_active(InputBinding binding)
    {
        return switch (binding.type())
        {
            case KEY   -> keys[BINDINGS.get(binding)];
            case MOUSE -> mouse[BINDINGS.get(binding)];
        };
    }
}
