package com.makeasaying.editor.core.input;

import com.badlogic.gdx.Input;
import imgui.flag.ImGuiKey;

/**
 * Pure mapping from libGDX Input.Keys to Dear ImGui 1.89 ImGuiKey constants.
 * Does not depend on active GL context.
 */
public final class GdxKeyToImGuiKey {
    private GdxKeyToImGuiKey() {}

    public static int toImGuiKey(int gdxKeyCode) {
        return switch (gdxKeyCode) {
            case Input.Keys.TAB -> ImGuiKey.Tab;
            case Input.Keys.LEFT -> ImGuiKey.LeftArrow;
            case Input.Keys.RIGHT -> ImGuiKey.RightArrow;
            case Input.Keys.UP -> ImGuiKey.UpArrow;
            case Input.Keys.DOWN -> ImGuiKey.DownArrow;
            case Input.Keys.PAGE_UP -> ImGuiKey.PageUp;
            case Input.Keys.PAGE_DOWN -> ImGuiKey.PageDown;
            case Input.Keys.HOME -> ImGuiKey.Home;
            case Input.Keys.END -> ImGuiKey.End;
            case Input.Keys.INSERT -> ImGuiKey.Insert;
            case Input.Keys.FORWARD_DEL -> ImGuiKey.Delete;
            case Input.Keys.BACKSPACE -> ImGuiKey.Backspace;
            case Input.Keys.SPACE -> ImGuiKey.Space;
            case Input.Keys.ENTER -> ImGuiKey.Enter;
            case Input.Keys.ESCAPE -> ImGuiKey.Escape;
            case Input.Keys.APOSTROPHE -> ImGuiKey.Apostrophe;
            case Input.Keys.COMMA -> ImGuiKey.Comma;
            case Input.Keys.MINUS -> ImGuiKey.Minus;
            case Input.Keys.PERIOD -> ImGuiKey.Period;
            case Input.Keys.SLASH -> ImGuiKey.Slash;
            case Input.Keys.SEMICOLON -> ImGuiKey.Semicolon;
            case Input.Keys.EQUALS -> ImGuiKey.Equal;
            case Input.Keys.LEFT_BRACKET -> ImGuiKey.LeftBracket;
            case Input.Keys.BACKSLASH -> ImGuiKey.Backslash;
            case Input.Keys.RIGHT_BRACKET -> ImGuiKey.RightBracket;
            case Input.Keys.GRAVE -> ImGuiKey.GraveAccent;
            case Input.Keys.CAPS_LOCK -> ImGuiKey.CapsLock;
            case Input.Keys.SCROLL_LOCK -> ImGuiKey.ScrollLock;
            case Input.Keys.NUM_LOCK -> ImGuiKey.NumLock;
            case Input.Keys.PRINT_SCREEN -> ImGuiKey.PrintScreen;
            case Input.Keys.PAUSE -> ImGuiKey.Pause;
            case Input.Keys.NUMPAD_0 -> ImGuiKey.Keypad0;
            case Input.Keys.NUMPAD_1 -> ImGuiKey.Keypad1;
            case Input.Keys.NUMPAD_2 -> ImGuiKey.Keypad2;
            case Input.Keys.NUMPAD_3 -> ImGuiKey.Keypad3;
            case Input.Keys.NUMPAD_4 -> ImGuiKey.Keypad4;
            case Input.Keys.NUMPAD_5 -> ImGuiKey.Keypad5;
            case Input.Keys.NUMPAD_6 -> ImGuiKey.Keypad6;
            case Input.Keys.NUMPAD_7 -> ImGuiKey.Keypad7;
            case Input.Keys.NUMPAD_8 -> ImGuiKey.Keypad8;
            case Input.Keys.NUMPAD_9 -> ImGuiKey.Keypad9;
            case Input.Keys.NUMPAD_DOT -> ImGuiKey.KeypadDecimal;
            case Input.Keys.NUMPAD_DIVIDE -> ImGuiKey.KeypadDivide;
            case Input.Keys.NUMPAD_MULTIPLY -> ImGuiKey.KeypadMultiply;
            case Input.Keys.NUMPAD_SUBTRACT -> ImGuiKey.KeypadSubtract;
            case Input.Keys.NUMPAD_ADD -> ImGuiKey.KeypadAdd;
            case Input.Keys.NUMPAD_ENTER -> ImGuiKey.KeypadEnter;
            case Input.Keys.NUMPAD_EQUALS -> ImGuiKey.KeypadEqual;
            case Input.Keys.SHIFT_LEFT -> ImGuiKey.LeftShift;
            case Input.Keys.CONTROL_LEFT -> ImGuiKey.LeftCtrl;
            case Input.Keys.ALT_LEFT -> ImGuiKey.LeftAlt;
            case Input.Keys.SYM -> ImGuiKey.LeftSuper;
            case Input.Keys.SHIFT_RIGHT -> ImGuiKey.RightShift;
            case Input.Keys.CONTROL_RIGHT -> ImGuiKey.RightCtrl;
            case Input.Keys.ALT_RIGHT -> ImGuiKey.RightAlt;
            case Input.Keys.MENU -> ImGuiKey.Menu;
            case Input.Keys.NUM_0 -> ImGuiKey._0;
            case Input.Keys.NUM_1 -> ImGuiKey._1;
            case Input.Keys.NUM_2 -> ImGuiKey._2;
            case Input.Keys.NUM_3 -> ImGuiKey._3;
            case Input.Keys.NUM_4 -> ImGuiKey._4;
            case Input.Keys.NUM_5 -> ImGuiKey._5;
            case Input.Keys.NUM_6 -> ImGuiKey._6;
            case Input.Keys.NUM_7 -> ImGuiKey._7;
            case Input.Keys.NUM_8 -> ImGuiKey._8;
            case Input.Keys.NUM_9 -> ImGuiKey._9;
            case Input.Keys.A -> ImGuiKey.A;
            case Input.Keys.B -> ImGuiKey.B;
            case Input.Keys.C -> ImGuiKey.C;
            case Input.Keys.D -> ImGuiKey.D;
            case Input.Keys.E -> ImGuiKey.E;
            case Input.Keys.F -> ImGuiKey.F;
            case Input.Keys.G -> ImGuiKey.G;
            case Input.Keys.H -> ImGuiKey.H;
            case Input.Keys.I -> ImGuiKey.I;
            case Input.Keys.J -> ImGuiKey.J;
            case Input.Keys.K -> ImGuiKey.K;
            case Input.Keys.L -> ImGuiKey.L;
            case Input.Keys.M -> ImGuiKey.M;
            case Input.Keys.N -> ImGuiKey.N;
            case Input.Keys.O -> ImGuiKey.O;
            case Input.Keys.P -> ImGuiKey.P;
            case Input.Keys.Q -> ImGuiKey.Q;
            case Input.Keys.R -> ImGuiKey.R;
            case Input.Keys.S -> ImGuiKey.S;
            case Input.Keys.T -> ImGuiKey.T;
            case Input.Keys.U -> ImGuiKey.U;
            case Input.Keys.V -> ImGuiKey.V;
            case Input.Keys.W -> ImGuiKey.W;
            case Input.Keys.X -> ImGuiKey.X;
            case Input.Keys.Y -> ImGuiKey.Y;
            case Input.Keys.Z -> ImGuiKey.Z;
            case Input.Keys.F1 -> ImGuiKey.F1;
            case Input.Keys.F2 -> ImGuiKey.F2;
            case Input.Keys.F3 -> ImGuiKey.F3;
            case Input.Keys.F4 -> ImGuiKey.F4;
            case Input.Keys.F5 -> ImGuiKey.F5;
            case Input.Keys.F6 -> ImGuiKey.F6;
            case Input.Keys.F7 -> ImGuiKey.F7;
            case Input.Keys.F8 -> ImGuiKey.F8;
            case Input.Keys.F9 -> ImGuiKey.F9;
            case Input.Keys.F10 -> ImGuiKey.F10;
            case Input.Keys.F11 -> ImGuiKey.F11;
            case Input.Keys.F12 -> ImGuiKey.F12;
            default -> ImGuiKey.None;
        };
    }
}
