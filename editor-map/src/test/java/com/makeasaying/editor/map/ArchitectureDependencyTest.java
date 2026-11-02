package com.makeasaying.editor.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ArchitectureDependencyTest {

    @Test
    void editorMapMustNeverDependOnGameModule() {
        // Enforce AGENTS.md Principle 4.1: editor-map depends on engine, data, and editor-core, never on game.
        ClassLoader classLoader = getClass().getClassLoader();
        assertThrows(ClassNotFoundException.class, () ->
            Class.forName("com.makeasaying.game.PlatformerGame", false, classLoader)
        );
    }
}
