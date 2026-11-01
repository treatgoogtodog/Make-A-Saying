package com.makeasaying.editor.core.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class UndoStackTest {
    private UndoStack stack;
    private AtomicInteger state;

    private static class IncrementCommand implements EditorCommand {
        private final AtomicInteger target;
        private final int delta;

        IncrementCommand(AtomicInteger target, int delta) {
            this.target = target;
            this.delta = delta;
        }

        @Override public String getName() { return "Increment " + delta; }
        @Override public void apply() { target.addAndGet(delta); }
        @Override public void revert() { target.addAndGet(-delta); }
    }

    @BeforeEach
    void setUp() {
        stack = new UndoStack(5);
        state = new AtomicInteger(0);
    }

    @Test
    void testBasicUndoRedo() {
        stack.execute(new IncrementCommand(state, 10));
        assertEquals(10, state.get());
        assertTrue(stack.canUndo());
        assertFalse(stack.canRedo());

        stack.undo();
        assertEquals(0, state.get());
        assertFalse(stack.canUndo());
        assertTrue(stack.canRedo());

        stack.redo();
        assertEquals(10, state.get());
        assertTrue(stack.canUndo());
        assertFalse(stack.canRedo());
    }

    @Test
    void testRedoStackClearedOnNewCommand() {
        stack.execute(new IncrementCommand(state, 5));
        stack.execute(new IncrementCommand(state, 5));
        assertEquals(10, state.get());

        stack.undo(); // now 5
        assertEquals(5, state.get());
        assertTrue(stack.canRedo());

        // Execute new command -> redo history must be cleared!
        stack.execute(new IncrementCommand(state, 100));
        assertEquals(105, state.get());
        assertFalse(stack.canRedo());

        stack.undo();
        assertEquals(5, state.get());
    }

    @Test
    void testGroupingMultipleCommandsIntoOneUndoStep() {
        stack.beginGroup("Paint Stroke");
        stack.execute(new IncrementCommand(state, 1));
        stack.execute(new IncrementCommand(state, 2));
        stack.execute(new IncrementCommand(state, 3));
        stack.endGroup();

        assertEquals(6, state.get());
        assertEquals("Paint Stroke", stack.getUndoActionName());

        // Exactly one undo steps reverts all three operations
        stack.undo();
        assertEquals(0, state.get());

        stack.redo();
        assertEquals(6, state.get());
    }

    @Test
    void testDirtyStateDerivation() {
        assertFalse(stack.isDirty());
        stack.execute(new IncrementCommand(state, 1));
        assertTrue(stack.isDirty());

        stack.markSaved();
        assertFalse(stack.isDirty());

        stack.execute(new IncrementCommand(state, 2));
        assertTrue(stack.isDirty());

        stack.undo(); // back at saved state
        assertFalse(stack.isDirty(), "Returning to saved position via undo must clear dirty flag");

        stack.redo();
        assertTrue(stack.isDirty());
    }

    @Test
    void testMaxDepthLimit() {
        UndoStack depthLimited = new UndoStack(3);
        depthLimited.execute(new IncrementCommand(state, 1));
        depthLimited.execute(new IncrementCommand(state, 2));
        depthLimited.execute(new IncrementCommand(state, 3));
        depthLimited.execute(new IncrementCommand(state, 4)); // Command 1 dropped

        assertEquals(10, state.get());
        depthLimited.undo(); // reverts 4 -> 6
        depthLimited.undo(); // reverts 3 -> 3
        depthLimited.undo(); // reverts 2 -> 1
        assertFalse(depthLimited.canUndo(), "Command 1 was dropped by depth limit so cannot undo further");
        assertEquals(1, state.get());
    }
}
