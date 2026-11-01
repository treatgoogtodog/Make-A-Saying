package com.makeasaying.engine.input;

import com.badlogic.gdx.Input;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Stable key string to libGDX Input.Keys mapping per AGENTS.md section 7.4.
 * Uses stable string names (e.g. "KEY_W", "KEY_ESCAPE") rather than raw integers.
 */
public final class KeyNames {
    private static final Map<String, Integer> NAME_TO_CODE = new HashMap<>();
    private static final Map<Integer, String> CODE_TO_NAME = new HashMap<>();

    static {
        registerKey("KEY_A", Input.Keys.A);
        registerKey("KEY_B", Input.Keys.B);
        registerKey("KEY_C", Input.Keys.C);
        registerKey("KEY_D", Input.Keys.D);
        registerKey("KEY_E", Input.Keys.E);
        registerKey("KEY_F", Input.Keys.F);
        registerKey("KEY_G", Input.Keys.G);
        registerKey("KEY_H", Input.Keys.H);
        registerKey("KEY_I", Input.Keys.I);
        registerKey("KEY_J", Input.Keys.J);
        registerKey("KEY_K", Input.Keys.K);
        registerKey("KEY_L", Input.Keys.L);
        registerKey("KEY_M", Input.Keys.M);
        registerKey("KEY_N", Input.Keys.N);
        registerKey("KEY_O", Input.Keys.O);
        registerKey("KEY_P", Input.Keys.P);
        registerKey("KEY_Q", Input.Keys.Q);
        registerKey("KEY_R", Input.Keys.R);
        registerKey("KEY_S", Input.Keys.S);
        registerKey("KEY_T", Input.Keys.T);
        registerKey("KEY_U", Input.Keys.U);
        registerKey("KEY_V", Input.Keys.V);
        registerKey("KEY_W", Input.Keys.W);
        registerKey("KEY_X", Input.Keys.X);
        registerKey("KEY_Y", Input.Keys.Y);
        registerKey("KEY_Z", Input.Keys.Z);

        registerKey("KEY_0", Input.Keys.NUM_0);
        registerKey("KEY_1", Input.Keys.NUM_1);
        registerKey("KEY_2", Input.Keys.NUM_2);
        registerKey("KEY_3", Input.Keys.NUM_3);
        registerKey("KEY_4", Input.Keys.NUM_4);
        registerKey("KEY_5", Input.Keys.NUM_5);
        registerKey("KEY_6", Input.Keys.NUM_6);
        registerKey("KEY_7", Input.Keys.NUM_7);
        registerKey("KEY_8", Input.Keys.NUM_8);
        registerKey("KEY_9", Input.Keys.NUM_9);

        registerKey("KEY_UP", Input.Keys.UP);
        registerKey("KEY_DOWN", Input.Keys.DOWN);
        registerKey("KEY_LEFT", Input.Keys.LEFT);
        registerKey("KEY_RIGHT", Input.Keys.RIGHT);

        registerKey("KEY_SPACE", Input.Keys.SPACE);
        registerKey("KEY_ENTER", Input.Keys.ENTER);
        registerKey("KEY_ESCAPE", Input.Keys.ESCAPE);
        registerKey("KEY_BACKSPACE", Input.Keys.BACKSPACE);
        registerKey("KEY_TAB", Input.Keys.TAB);
        registerKey("KEY_SHIFT_LEFT", Input.Keys.SHIFT_LEFT);
        registerKey("KEY_SHIFT_RIGHT", Input.Keys.SHIFT_RIGHT);
        registerKey("KEY_CONTROL_LEFT", Input.Keys.CONTROL_LEFT);
        registerKey("KEY_CONTROL_RIGHT", Input.Keys.CONTROL_RIGHT);

        registerKey("KEY_F1", Input.Keys.F1);
        registerKey("KEY_F2", Input.Keys.F2);
        registerKey("KEY_F3", Input.Keys.F3);
        registerKey("KEY_F4", Input.Keys.F4);
        registerKey("KEY_F5", Input.Keys.F5);
        registerKey("KEY_F6", Input.Keys.F6);
        registerKey("KEY_F7", Input.Keys.F7);
        registerKey("KEY_F8", Input.Keys.F8);
        registerKey("KEY_F9", Input.Keys.F9);
        registerKey("KEY_F10", Input.Keys.F10);
        registerKey("KEY_F11", Input.Keys.F11);
        registerKey("KEY_F12", Input.Keys.F12);
    }

    private static void registerKey(String name, int code) {
        NAME_TO_CODE.put(name, code);
        CODE_TO_NAME.put(code, name);
    }

    private KeyNames() {}

    public static boolean isValidKeyName(String name) {
        return name != null && NAME_TO_CODE.containsKey(name);
    }

    public static int toKeyCode(String name) {
        Integer code = NAME_TO_CODE.get(name);
        if (code == null) {
            throw new IllegalArgumentException("Unknown key name: " + name);
        }
        return code;
    }

    public static String toKeyName(int keyCode) {
        return CODE_TO_NAME.getOrDefault(keyCode, "UNKNOWN_KEY_" + keyCode);
    }

    public static Map<String, Integer> getAllKnownKeys() {
        return Collections.unmodifiableMap(NAME_TO_CODE);
    }
}
