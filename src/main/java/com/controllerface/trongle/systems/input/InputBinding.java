package com.controllerface.trongle.systems.input;

public enum InputBinding
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

    InputBinding(BindingType bindingType)
    {
        binding_type = bindingType;
    }

    public enum BindingType
    {
        KEY,
        MOUSE
    }

    public final BindingType binding_type;
}
