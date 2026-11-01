package com.makeasaying.editor.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ArchitectureDependencyTest {
    @Test
    void editorsMustNeverDependOnGameModule() {
        // Enforce M6a architecture rule: editors depend on engine, data, editor-core only; never on game.
        ClassLoader classLoader = getClass().getClassLoader();
        assertThrows(ClassNotFoundException.class, () ->
            Class.forName("com.makeasaying.game.PlatformerGame", false, classLoader)
        );
    }
}
