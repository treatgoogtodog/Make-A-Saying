package com.makeasaying.engine.input;

public record ActionDefinition(
    String id,
    ActionType type,
    String negativeButton,
    String positiveButton,
    boolean reserved
) {
    public enum ActionType {
        BUTTON,
        AXIS
    }

    public static ActionDefinition button(String id, boolean reserved) {
        return new ActionDefinition(id, ActionType.BUTTON, null, null, reserved);
    }

    public static ActionDefinition button(String id) {
        return button(id, false);
    }

    public static ActionDefinition axis(String id, String negativeButton, String positiveButton) {
        return new ActionDefinition(id, ActionType.AXIS, negativeButton, positiveButton, false);
    }
}
