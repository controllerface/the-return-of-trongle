package com.controllerface.trongle.input;

import com.juncture.alloy.input.BindingType;
import com.juncture.alloy.input.IInputBinding;

public enum InputBinding implements IInputBinding<InputBinding>
{
    ESCAPE(BindingType.KEY),

    MOVE_FORWARD(BindingType.KEY),
    MOVE_BACKWARD(BindingType.KEY),
    MOVE_LEFT(BindingType.KEY),
    MOVE_RIGHT(BindingType.KEY),
    ROTATE_LEFT(BindingType.KEY),
    ROTATE_RIGHT(BindingType.KEY),

    PRIMARY_FIRE(BindingType.MOUSE),
    CAMERA_ADJUST(BindingType.MOUSE),
    SECONDARY_FIRE(BindingType.MOUSE),
    MOUSE_BACK(BindingType.MOUSE),
    MOUSE_FORWARD(BindingType.MOUSE),

    ;

    private final BindingType binding_type;
    InputBinding(BindingType bindingType) { binding_type = bindingType; }
    @Override public BindingType type() { return binding_type; }
}
