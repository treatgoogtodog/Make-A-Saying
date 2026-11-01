package com.makeasaying.editor.core.command;

/**
 * Fundamental undoable/redoable editor command per AGENTS.md section 4.1 and M6a brief.
 * Lives in editor-core (pure Java, zero GL/ImGui dependencies).
 */
public interface EditorCommand {
    String getName();

    void apply();

    void revert();

    default boolean canMergeWith(EditorCommand next) {
        return false;
    }

    default void mergeWith(EditorCommand next) {
        throw new UnsupportedOperationException("Merging not supported for " + getName());
    }
}
