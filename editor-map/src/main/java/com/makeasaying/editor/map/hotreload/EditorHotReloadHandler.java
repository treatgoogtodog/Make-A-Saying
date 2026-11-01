package com.makeasaying.editor.map.hotreload;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Pure handler for bidirectional editor hot-reload interactions per M6a brief.
 * - Auto-reloads from disk if document has clean undo stack (!isDirty).
 * - Enters conflict state and prompts user if in-memory document is dirty.
 * - Suppresses watcher reload events caused by the editor's own saves.
 */
public final class EditorHotReloadHandler {
    public enum ConflictState {
        NONE,
        CONFLICT_PROMPT
    }

    private ConflictState conflictState = ConflictState.NONE;
    private boolean suppressNextWatcherEvent = false;
    private final Consumer<Path> reloadFromDiskCallback;

    public EditorHotReloadHandler(Consumer<Path> reloadFromDiskCallback) {
        this.reloadFromDiskCallback = reloadFromDiskCallback;
    }

    public void onEditorSaved() {
        this.suppressNextWatcherEvent = true;
    }

    public void onDiskFileChanged(Path changedPath, boolean isDirty) {
        if (suppressNextWatcherEvent) {
            suppressNextWatcherEvent = false;
            return;
        }

        if (!isDirty) {
            // Clean state: auto-reload seamlessly
            if (reloadFromDiskCallback != null) {
                reloadFromDiskCallback.accept(changedPath);
            }
        } else {
            // Dirty state: prompt user
            conflictState = ConflictState.CONFLICT_PROMPT;
        }
    }

    public boolean isConflictPromptOpen() {
        return conflictState == ConflictState.CONFLICT_PROMPT;
    }

    public void resolveConflictReloadDisk(Path path) {
        conflictState = ConflictState.NONE;
        if (reloadFromDiskCallback != null) {
            reloadFromDiskCallback.accept(path);
        }
    }

    public void resolveConflictKeepInMemory() {
        conflictState = ConflictState.NONE;
    }
}
