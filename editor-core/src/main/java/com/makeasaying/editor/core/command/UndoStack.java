package com.makeasaying.editor.core.command;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Robust Undo/Redo stack with grouping, depth limit, and dirty state tracking per M6a brief.
 * Pure logic, headless testable.
 */
public final class UndoStack {
    public static final int DEFAULT_MAX_DEPTH = 100;

    private final int maxDepth;
    private final List<EditorCommand> history = new ArrayList<>();
    private final Deque<EditorCommand> redoStack = new ArrayDeque<>();

    private int currentIndex = 0; // points to next insert slot (history size)
    private int savedIndex = 0;   // index in history when document was last saved

    private CompoundCommand currentGroup = null;

    public UndoStack(int maxDepth) {
        if (maxDepth <= 0) throw new IllegalArgumentException("maxDepth must be positive");
        this.maxDepth = maxDepth;
    }

    public UndoStack() {
        this(DEFAULT_MAX_DEPTH);
    }

    public void execute(EditorCommand command) {
        if (command == null) return;

        if (currentGroup != null) {
            command.apply();
            currentGroup.add(command);
            return;
        }

        command.apply();

        // Clear redo stack on new command
        redoStack.clear();

        // Check if command can merge with the top command
        if (currentIndex > 0) {
            EditorCommand top = history.get(currentIndex - 1);
            if (top.canMergeWith(command)) {
                top.mergeWith(command);
                return;
            }
        }

        // Truncate history beyond currentIndex if we had undone and are now applying
        while (history.size() > currentIndex) {
            history.remove(history.size() - 1);
        }

        history.add(command);
        currentIndex++;

        // Enforce max depth
        if (history.size() > maxDepth) {
            history.remove(0);
            currentIndex--;
            if (savedIndex > 0) {
                savedIndex--;
            } else {
                savedIndex = -1; // Saved state fell off the stack
            }
        }
    }

    public void beginGroup(String name) {
        if (currentGroup != null) {
            throw new IllegalStateException("Already in a command group: " + currentGroup.getName());
        }
        currentGroup = new CompoundCommand(name);
    }

    public void endGroup() {
        if (currentGroup == null) {
            throw new IllegalStateException("Not in a command group");
        }
        CompoundCommand group = currentGroup;
        currentGroup = null;

        if (!group.isEmpty()) {
            redoStack.clear();
            while (history.size() > currentIndex) {
                history.remove(history.size() - 1);
            }
            history.add(group);
            currentIndex++;

            if (history.size() > maxDepth) {
                history.remove(0);
                currentIndex--;
                if (savedIndex > 0) savedIndex--;
                else savedIndex = -1;
            }
        }
    }

    public boolean isGrouping() {
        return currentGroup != null;
    }

    public boolean canUndo() {
        return currentIndex > 0 && currentGroup == null;
    }

    public boolean canRedo() {
        return !redoStack.isEmpty() && currentGroup == null;
    }

    public void undo() {
        if (!canUndo()) return;

        currentIndex--;
        EditorCommand command = history.get(currentIndex);
        command.revert();
        redoStack.push(command);
    }

    public void redo() {
        if (!canRedo()) return;

        EditorCommand command = redoStack.pop();
        command.apply();
        currentIndex++;
    }

    public void markSaved() {
        this.savedIndex = currentIndex;
    }

    public boolean isDirty() {
        return currentIndex != savedIndex;
    }

    public void clear() {
        history.clear();
        redoStack.clear();
        currentIndex = 0;
        savedIndex = 0;
        currentGroup = null;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public int getSavedIndex() {
        return savedIndex;
    }

    public String getUndoActionName() {
        if (!canUndo()) return "";
        return history.get(currentIndex - 1).getName();
    }

    public String getRedoActionName() {
        if (!canRedo()) return "";
        return redoStack.peek().getName();
    }
}
