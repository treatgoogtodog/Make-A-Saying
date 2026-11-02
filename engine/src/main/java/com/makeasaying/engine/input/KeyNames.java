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
    public static final int MOD_NONE = 0;
    public static final int MOD_CTRL = 1;
    public static final int MOD_SHIFT = 2;
    public static final int MOD_ALT = 4;

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
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (NAME_TO_CODE.containsKey(name)) {
            return true;
        }
        if (name.contains("+")) {
            String[] parts = name.split("\\+");
            if (parts.length < 2) return false;
            for (int i = 0; i < parts.length - 1; i++) {
                String mod = parts[i].trim().toUpperCase(java.util.Locale.ROOT);
                if (!mod.equals("CTRL") && !mod.equals("CONTROL") && !mod.equals("SHIFT") && !mod.equals("ALT")) {
                    return false;
                }
            }
            String baseKey = parts[parts.length - 1].trim();
            return NAME_TO_CODE.containsKey(baseKey);
        }
        return false;
    }

    public static int toKeyCode(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Key name cannot be null");
        }
        if (NAME_TO_CODE.containsKey(name)) {
            return NAME_TO_CODE.get(name);
        }
        if (name.contains("+")) {
            String[] parts = name.split("\\+");
            int modMask = 0;
            for (int i = 0; i < parts.length - 1; i++) {
                String mod = parts[i].trim().toUpperCase(java.util.Locale.ROOT);
                if (mod.equals("CTRL") || mod.equals("CONTROL")) {
                    modMask |= MOD_CTRL;
                } else if (mod.equals("SHIFT")) {
                    modMask |= MOD_SHIFT;
                } else if (mod.equals("ALT")) {
                    modMask |= MOD_ALT;
                } else {
                    throw new IllegalArgumentException("Unknown modifier: " + parts[i] + " in " + name);
                }
            }
            String baseKey = parts[parts.length - 1].trim();
            Integer baseCode = NAME_TO_CODE.get(baseKey);
            if (baseCode == null) {
                throw new IllegalArgumentException("Unknown base key name: " + baseKey + " in " + name);
            }
            return (modMask << 16) | baseCode;
        }
        throw new IllegalArgumentException("Unknown key name: " + name);
    }

    public static String toKeyName(int keyCode) {
        int modMask = (keyCode >> 16) & 0xFFFF;
        int baseCode = keyCode & 0xFFFF;
        if (modMask == 0) {
            return CODE_TO_NAME.getOrDefault(baseCode, "UNKNOWN_KEY_" + baseCode);
        }
        StringBuilder sb = new StringBuilder();
        if ((modMask & MOD_CTRL) != 0) sb.append("CTRL+");
        if ((modMask & MOD_SHIFT) != 0) sb.append("SHIFT+");
        if ((modMask & MOD_ALT) != 0) sb.append("ALT+");
        sb.append(CODE_TO_NAME.getOrDefault(baseCode, "UNKNOWN_KEY_" + baseCode));
        return sb.toString();
    }

    public static Map<String, Integer> getAllKnownKeys() {
        return Collections.unmodifiableMap(NAME_TO_CODE);
    }
}
