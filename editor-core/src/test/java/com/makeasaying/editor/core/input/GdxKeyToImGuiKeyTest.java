package com.makeasaying.editor.core.input;

import com.badlogic.gdx.Input;
import imgui.flag.ImGuiKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GdxKeyToImGuiKeyTest {
    @Test
    void testBasicAlphaAndMovementKeysMapped() {
        assertEquals(ImGuiKey.W, GdxKeyToImGuiKey.toImGuiKey(Input.Keys.W));
        assertEquals(ImGuiKey.A, GdxKeyToImGuiKey.toImGuiKey(Input.Keys.A));
        assertEquals(ImGuiKey.S, GdxKeyToImGuiKey.toImGuiKey(Input.Keys.S));
        assertEquals(ImGuiKey.D, GdxKeyToImGuiKey.toImGuiKey(Input.Keys.D));
        assertEquals(ImGuiKey.Space, GdxKeyToImGuiKey.toImGuiKey(Input.Keys.SPACE));
        assertEquals(ImGuiKey.Escape, GdxKeyToImGuiKey.toImGuiKey(Input.Keys.ESCAPE));
        assertEquals(ImGuiKey.Enter, GdxKeyToImGuiKey.toImGuiKey(Input.Keys.ENTER));
    }

    @Test
    void testFunctionKeysAndModifiers() {
        assertEquals(ImGuiKey.F1, GdxKeyToImGuiKey.toImGuiKey(Input.Keys.F1));
        assertEquals(ImGuiKey.F5, GdxKeyToImGuiKey.toImGuiKey(Input.Keys.F5));
        assertEquals(ImGuiKey.LeftShift, GdxKeyToImGuiKey.toImGuiKey(Input.Keys.SHIFT_LEFT));
        assertEquals(ImGuiKey.LeftCtrl, GdxKeyToImGuiKey.toImGuiKey(Input.Keys.CONTROL_LEFT));
        assertEquals(ImGuiKey.LeftAlt, GdxKeyToImGuiKey.toImGuiKey(Input.Keys.ALT_LEFT));
    }

    @Test
    void testUnknownKeyReturnsNone() {
        assertEquals(ImGuiKey.None, GdxKeyToImGuiKey.toImGuiKey(-999));
    }
}
