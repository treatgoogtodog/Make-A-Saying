package com.makeasaying.engine.input;

/**
 * Well-known action ID constants.
 */
public final class Actions {
    private Actions() {}

    public static final String MOVE_LEFT = "move_left";
    public static final String MOVE_RIGHT = "move_right";
    public static final String MOVE_UP = "move_up";
    public static final String MOVE_DOWN = "move_down";
    public static final String MOVE_X = "move_x";
    public static final String MOVE_Y = "move_y";
    public static final String JUMP = "jump";
    public static final String ATTACK = "attack";
    public static final String INTERACT = "interact";
    public static final String CANCEL = "cancel";
    public static final String PAUSE = "pause";

    public static final String DEBUG_RECORD = "debug_record";
    public static final String DEBUG_PLAYBACK = "debug_playback";
    public static final String DEBUG_FLAGS = "debug_flags";
    public static final String DEBUG_LOAD_CHECKPOINT = "debug_load_checkpoint";

    public static final String TOGGLE_EDITOR = "toggle_editor";
    public static final String EDITOR_PLAY = "editor_play";
    public static final String EDITOR_STOP = "editor_stop";
    public static final String EDITOR_UNDO = "editor_undo";
    public static final String EDITOR_REDO = "editor_redo";
}
