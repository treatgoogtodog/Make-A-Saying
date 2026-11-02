package com.makeasaying.engine.input;

import com.badlogic.gdx.Input;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;
import com.makeasaying.engine.util.LogEntry;
import com.makeasaying.engine.util.LogLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InputManagerTest {
    private ActionRegistry registry;
    private ContextBindings bindings;
    private InputContextStack contextStack;
    private InputManager inputManager;

    @BeforeEach
    void setUp() {
        Log.clear();

        registry = new ActionRegistry();
        registry.register(ActionDefinition.button("move_left"));
        registry.register(ActionDefinition.button("move_right"));
        registry.register(ActionDefinition.button("jump"));
        registry.register(ActionDefinition.button("interact"));
        registry.register(ActionDefinition.button("editor_undo"));
        registry.register(ActionDefinition.button("editor_redo"));
        registry.register(ActionDefinition.button("cancel", true));
        registry.register(ActionDefinition.button("pause", true));
        registry.register(ActionDefinition.axis("move_x", "move_left", "move_right"));

        bindings = new ContextBindings();
        bindings.bind(InputContext.GAMEPLAY, "move_left", Input.Keys.A);
        bindings.bind(InputContext.GAMEPLAY, "move_right", Input.Keys.D);
        bindings.bind(InputContext.GAMEPLAY, "jump", Input.Keys.SPACE);
        bindings.bind(InputContext.GAMEPLAY, "cancel", Input.Keys.ESCAPE);

        bindings.bind(InputContext.MENU, "interact", Input.Keys.SPACE);
        bindings.bind(InputContext.MENU, "cancel", Input.Keys.ESCAPE);

        bindings.bind(InputContext.GLOBAL, "pause", Input.Keys.ESCAPE);

        contextStack = new InputContextStack(InputContext.GAMEPLAY);
        inputManager = new InputManager(registry, bindings, contextStack);
    }

    @Test
    void testSubTickTapYieldsPressedThenReleased() {
        // Enqueue key press and key release in the same frame
        inputManager.keyDown(Input.Keys.SPACE);
        inputManager.keyUp(Input.Keys.SPACE);

        // Tick 1: should see pressed and down
        inputManager.tick(1L, true);
        InputState state = inputManager.getState();
        assertTrue(state.isDown("jump"), "Tick 1: jump must be down");
        assertTrue(state.isPressed("jump"), "Tick 1: jump must be pressed");
        assertFalse(state.isReleased("jump"), "Tick 1: jump must not be released");

        // Tick 2: release was queued behind same-tick press, so tick 2 sees released
        inputManager.tick(2L, true);
        assertFalse(state.isDown("jump"), "Tick 2: jump must not be down");
        assertFalse(state.isPressed("jump"), "Tick 2: jump must not be pressed");
        assertTrue(state.isReleased("jump"), "Tick 2: jump must be released");
    }

    @Test
    void testCatchUpTicksDoNotRepeatPressed() {
        inputManager.keyDown(Input.Keys.SPACE);

        // Tick 0 (first tick of frame)
        inputManager.tick(10L, true);
        assertTrue(inputManager.getState().isPressed("jump"));
        assertTrue(inputManager.getState().isDown("jump"));

        // Tick 1 (catch-up tick, isFirstTickOfFrame = false)
        inputManager.tick(11L, false);
        assertFalse(inputManager.getState().isPressed("jump"), "Catch-up tick must not repeat pressed");
        assertTrue(inputManager.getState().isDown("jump"), "Down remains held during catch-up");

        // Tick 2 (catch-up tick)
        inputManager.tick(12L, false);
        assertFalse(inputManager.getState().isPressed("jump"));
        assertTrue(inputManager.getState().isDown("jump"));
    }

    @Test
    void testZeroTickFramesPreserveQueuedEvents() {
        // Enqueue event, but 0 ticks run this frame
        inputManager.keyDown(Input.Keys.SPACE);
        assertEquals(1, inputManager.getRawQueue().size());

        // Zero-tick frame: tick() is not invoked, events remain in queue
        assertEquals(1, inputManager.getRawQueue().size());

        // Next frame runs a tick
        inputManager.tick(1L, true);
        assertEquals(0, inputManager.getRawQueue().size());
        assertTrue(inputManager.getState().isDown("jump"));
    }

    @Test
    void testLastPressedWinsOnOpposingMovementAxis() {
        InputState state = inputManager.getState();

        // 1. Press Left -> move_x = -1
        inputManager.keyDown(Input.Keys.A);
        inputManager.tick(1L, true);
        assertEquals(-1, state.getAxis("move_x"));

        // 2. While holding Left, press Right -> Right is newer -> move_x = 1
        inputManager.keyDown(Input.Keys.D);
        inputManager.tick(2L, true);
        assertEquals(1, state.getAxis("move_x"));

        // 3. Release Right while Left is still held -> Left resumes -> move_x = -1
        inputManager.keyUp(Input.Keys.D);
        inputManager.tick(3L, true);
        assertEquals(-1, state.getAxis("move_x"));

        // 4. Release Left -> move_x = 0
        inputManager.keyUp(Input.Keys.A);
        inputManager.tick(4L, true);
        assertEquals(0, state.getAxis("move_x"));
    }

    @Test
    void testContextChangeClearsFlagsAndSwallowsHeldKeys() {
        // Start in MENU context
        contextStack.push(InputContext.MENU);
        assertEquals(InputContext.MENU, contextStack.getActiveContext());

        // Press SPACE in menu (bound to "interact" in menu, but "jump" in gameplay)
        inputManager.keyDown(Input.Keys.SPACE);
        inputManager.tick(1L, true);
        assertTrue(inputManager.getState().isDown("interact"));

        // Context switches to GAMEPLAY while SPACE is still held
        contextStack.pop();
        assertEquals(InputContext.GAMEPLAY, contextStack.getActiveContext());

        // In gameplay on next tick, SPACE must be swallowed (not registered as jump!)
        inputManager.tick(2L, true);
        assertFalse(inputManager.getState().isDown("jump"), "Held key must be swallowed by new context");
        assertFalse(inputManager.getState().isPressed("jump"));
        assertTrue(inputManager.isKeySwallowed(Input.Keys.SPACE));

        // Release SPACE (unswallows the key)
        inputManager.keyUp(Input.Keys.SPACE);
        inputManager.tick(3L, true);
        assertFalse(inputManager.isKeySwallowed(Input.Keys.SPACE));

        // Press SPACE again in gameplay -> now jump is registered
        inputManager.keyDown(Input.Keys.SPACE);
        inputManager.tick(4L, true);
        assertTrue(inputManager.getState().isDown("jump"));
        assertTrue(inputManager.getState().isPressed("jump"));
    }

    @Test
    void testCtrlZModifierChordAndImGuiCapturePreemption() {
        registry.register(ActionDefinition.button("editor_undo"));
        int ctrlZ = (KeyNames.MOD_CTRL << 16) | Input.Keys.Z;
        bindings.bind(InputContext.EDITOR, "editor_undo", ctrlZ);

        contextStack.push(InputContext.EDITOR);
        assertEquals(InputContext.EDITOR, contextStack.getActiveContext());

        // 1. When ImGui text field has focus (wantsKeyboard = true), Ctrl+Z is captured and does NOT trigger editor_undo
        inputManager.setCaptureSource(new InputCaptureSource() {
            @Override
            public boolean wantsKeyboard() {
                return true;
            }

            @Override
            public boolean wantsMouse() {
                return false;
            }
        });

        inputManager.keyDown(Input.Keys.CONTROL_LEFT);
        inputManager.tick(1L, true);

        inputManager.keyDown(Input.Keys.Z);
        inputManager.tick(2L, true);
        assertFalse(inputManager.getState().isDown("editor_undo"), "When ImGui captures keyboard, Ctrl+Z must not trigger editor_undo");

        inputManager.keyUp(Input.Keys.Z);
        inputManager.tick(3L, true);

        // 2. When ImGui is not capturing keyboard (wantsKeyboard = false), Ctrl+Z triggers editor_undo
        inputManager.setCaptureSource(InputCaptureSource.none());
        inputManager.keyDown(Input.Keys.CONTROL_LEFT);
        inputManager.keyDown(Input.Keys.Z);
        inputManager.tick(4L, true);
        assertTrue(inputManager.getState().isDown("editor_undo"), "Ctrl+Z must trigger editor_undo when ImGui is not capturing keyboard");
        assertTrue(inputManager.getState().isPressed("editor_undo"));

        // 3. Releasing Z releases editor_undo
        inputManager.keyUp(Input.Keys.Z);
        inputManager.keyUp(Input.Keys.CONTROL_LEFT);
        inputManager.tick(5L, true);
        assertFalse(inputManager.getState().isDown("editor_undo"));
        assertTrue(inputManager.getState().isReleased("editor_undo"));
    }

    @Test
    void testRingBufferOverflowLogsLoudErrorAndDropsNewest() {
        RawInputQueue queue = new RawInputQueue(32);

        // Fill queue to capacity
        for (int i = 0; i < 32; i++) {
            assertTrue(queue.enqueue(Input.Keys.A, true));
        }
        assertEquals(32, queue.size());

        // Enqueue 33rd event -> must log error and reject
        boolean accepted = queue.enqueue(Input.Keys.B, true);
        assertFalse(accepted);
        assertEquals(32, queue.size());

        List<LogEntry> entries = Log.getEntries();
        boolean foundError = entries.stream().anyMatch(e ->
            e.level() == LogLevel.ERROR &&
            e.category() == LogCategory.INPUT &&
            e.message().contains("queue overflow"));
        assertTrue(foundError, "Queue overflow must log loud ERROR under INPUT");
    }

    @Test
    void testInputCaptureSourcePreemption() {
        // Set capture source to true (simulating ImGui taking focus)
        inputManager.setCaptureSource(new InputCaptureSource() {
            @Override
            public boolean wantsKeyboard() {
                return true;
            }

            @Override
            public boolean wantsMouse() {
                return false;
            }
        });

        inputManager.keyDown(Input.Keys.SPACE);
        inputManager.tick(1L, true);

        // Active context should NOT see the key
        assertFalse(inputManager.getState().isDown("jump"));

        // But global actions (e.g. pause) should still work
        inputManager.keyDown(Input.Keys.ESCAPE);
        inputManager.tick(2L, true);
        assertTrue(inputManager.getGlobalState().isDown("pause"));
    }
}
