package com.makeasaying.editor.map.dialogs;

import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.editor.map.hotreload.EditorHotReloadHandler;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class DialogModelsTest {

    @Test
    public void testNewRoomModelValidationAndCreation() {
        NewRoomModel model = new NewRoomModel();
        model.setId("invalid id with space!");
        assertFalse(model.isValid());
        assertThrows(IllegalStateException.class, model::createDocument);

        model.setId("cavern_01");
        model.setWidth(30);
        model.setHeight(20);
        assertTrue(model.isValid());

        RoomDocument doc = model.createDocument();
        assertEquals("cavern_01", doc.getId());
        assertEquals(30, doc.getWidth());
        assertEquals(20, doc.getHeight());
        assertEquals("test_area", doc.getTilesetAliases().get("a"));
        assertFalse(doc.getSpawnPoints().isEmpty());
    }

    @Test
    public void testSaveConfirmModel() {
        SaveConfirmModel model = new SaveConfirmModel();
        assertFalse(model.isOpen());

        AtomicBoolean saved = new AtomicBoolean(false);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        model.promptSaveWithErrors(3, () -> saved.set(true), () -> cancelled.set(true));
        assertTrue(model.isOpen());
        assertEquals(SaveConfirmModel.PromptType.CONFIRM_SAVE_WITH_ERRORS, model.getCurrentPrompt());
        assertEquals(3, model.getErrorCount());

        model.confirm();
        assertFalse(model.isOpen());
        assertTrue(saved.get());
        assertFalse(cancelled.get());
    }

    @Test
    public void testEditorHotReloadHandler() {
        AtomicReference<Path> reloaded = new AtomicReference<>();
        EditorHotReloadHandler handler = new EditorHotReloadHandler(reloaded::set);

        Path p = Path.of("content/rooms/test_01.json");

        // Clean document: auto-reload
        handler.onDiskFileChanged(p, false);
        assertEquals(p, reloaded.get());
        assertFalse(handler.isConflictPromptOpen());

        // Self-write suppression
        reloaded.set(null);
        handler.onEditorSaved();
        handler.onDiskFileChanged(p, false);
        assertNull(reloaded.get(), "Watcher event following editor save must be suppressed");

        // Dirty document: conflict prompt
        handler.onDiskFileChanged(p, true);
        assertTrue(handler.isConflictPromptOpen());
        assertNull(reloaded.get());

        // Resolve conflict by keeping in memory
        handler.resolveConflictKeepInMemory();
        assertFalse(handler.isConflictPromptOpen());
        assertNull(reloaded.get());

        // Resolve conflict by reloading disk
        handler.onDiskFileChanged(p, true);
        assertTrue(handler.isConflictPromptOpen());
        handler.resolveConflictReloadDisk(p);
        assertFalse(handler.isConflictPromptOpen());
        assertEquals(p, reloaded.get());
    }
}
