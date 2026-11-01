package com.makeasaying.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ArchitectureDependencyTest {
    @Test
    void dataMustNeverDependOnEditorModules() {
        // Enforce AGENTS.md principle 4.1: data module must never depend on editors.
        ClassLoader classLoader = getClass().getClassLoader();
        assertThrows(ClassNotFoundException.class, () ->
            Class.forName("com.makeasaying.editor.core.EditorCommand", false, classLoader)
        );
        assertThrows(ClassNotFoundException.class, () ->
            Class.forName("com.makeasaying.editor.map.document.RoomDocument", false, classLoader)
        );
    }
}
