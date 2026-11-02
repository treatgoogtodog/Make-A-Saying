package com.makeasaying.editor.map.hotreload;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EditorHotReloadHandlerTest {

    @Test
    void testCleanDocumentAutoReloads() {
        List<Path> reloads = new ArrayList<>();
        EditorHotReloadHandler handler = new EditorHotReloadHandler(reloads::add);

        Path path = Path.of("content/rooms/test_01.json");
        handler.onDiskFileChanged(path, false);

        assertEquals(1, reloads.size());
        assertEquals(path, reloads.get(0));
        assertFalse(handler.isConflictPromptOpen());
    }

    @Test
    void testDirtyDocumentOpensConflictPrompt() {
        List<Path> reloads = new ArrayList<>();
        EditorHotReloadHandler handler = new EditorHotReloadHandler(reloads::add);

        Path path = Path.of("content/rooms/test_01.json");
        handler.onDiskFileChanged(path, true);

        // Does NOT auto-reload dirty document
        assertTrue(reloads.isEmpty());
        assertTrue(handler.isConflictPromptOpen());

        // Resolving by reload
        handler.resolveConflictReloadDisk(path);
        assertFalse(handler.isConflictPromptOpen());
        assertEquals(1, reloads.size());
    }

    @Test
    void testDirtyDocumentCanKeepInMemory() {
        List<Path> reloads = new ArrayList<>();
        EditorHotReloadHandler handler = new EditorHotReloadHandler(reloads::add);

        Path path = Path.of("content/rooms/test_01.json");
        handler.onDiskFileChanged(path, true);
        assertTrue(handler.isConflictPromptOpen());

        handler.resolveConflictKeepInMemory();
        assertFalse(handler.isConflictPromptOpen());
        assertTrue(reloads.isEmpty());
    }

    @Test
    void testSelfWriteIsIgnored() {
        List<Path> reloads = new ArrayList<>();
        EditorHotReloadHandler handler = new EditorHotReloadHandler(reloads::add);

        handler.onEditorSaved();

        Path path = Path.of("content/rooms/test_01.json");
        handler.onDiskFileChanged(path, false);

        // Self-write is suppressed: no callback invoked
        assertTrue(reloads.isEmpty());
        assertFalse(handler.isConflictPromptOpen());

        // Next disk change is processed normally
        handler.onDiskFileChanged(path, false);
        assertEquals(1, reloads.size());
    }
}
