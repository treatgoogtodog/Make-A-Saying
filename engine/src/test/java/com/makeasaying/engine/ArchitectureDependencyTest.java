package com.makeasaying.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ArchitectureDependencyTest {

    @Test
    void engineMustNeverDependOnEditorModules() {
        // Enforce AGENTS.md principle 4.1: Engine never depends on editors.
        ClassLoader classLoader = getClass().getClassLoader();
        assertThrows(ClassNotFoundException.class, () ->
            Class.forName("com.makeasaying.editor.core.EditorCore", false, classLoader)
        );
        assertThrows(ClassNotFoundException.class, () ->
            Class.forName("com.makeasaying.editor.map.MapEditorModule", false, classLoader)
        );
        assertThrows(ClassNotFoundException.class, () ->
            Class.forName("com.makeasaying.editor.anim.AnimEditorModule", false, classLoader)
        );
        assertThrows(ClassNotFoundException.class, () ->
            Class.forName("com.makeasaying.editor.cut.CutsceneEditorModule", false, classLoader)
        );
    }

    @Test
    void engineMustNeverDependOnDataModule() {
        // Enforce DEC-002: engine defines runtime types and never reads files; data depends on engine.
        ClassLoader classLoader = getClass().getClassLoader();
        assertThrows(ClassNotFoundException.class, () ->
            Class.forName("com.makeasaying.data.config.ConfigLoader", false, classLoader)
        );
    }
}
