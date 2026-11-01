package com.makeasaying.engine.input;

import com.badlogic.gdx.InputAdapter;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

import java.util.HashSet;
import java.util.Set;

/**
 * Coordinates raw input events, bindings translation, context stack,
 * and deterministic per-tick draining into InputState.
 */
public final class InputManager extends InputAdapter {
    private static final int MAX_KEY_CODE = 512;

    private final ActionRegistry registry;
    private final ContextBindings bindings;
    private final InputContextStack contextStack;
    private final RawInputQueue rawQueue;
    private final InputState state;
    private final InputState globalState;

    private InputCaptureSource captureSource = InputCaptureSource.none();

    private final boolean[] physicallyHeldKeys = new boolean[MAX_KEY_CODE];
    private final boolean[] swallowedKeys = new boolean[MAX_KEY_CODE];
    private final boolean[] transitionedThisTick = new boolean[MAX_KEY_CODE];

    public InputManager(ActionRegistry registry, ContextBindings bindings, InputContextStack contextStack) {
        this.registry = registry;
        this.bindings = bindings;
        this.contextStack = contextStack;
        this.rawQueue = new RawInputQueue();
        this.state = new InputState(registry);
        this.globalState = new InputState(registry);

        contextStack.setOnContextChangeListener(this::onContextChanged);
    }

    public InputManager(ActionRegistry registry, ContextBindings bindings) {
        this(registry, bindings, new InputContextStack());
    }

    public void setCaptureSource(InputCaptureSource captureSource) {
        this.captureSource = (captureSource != null) ? captureSource : InputCaptureSource.none();
    }

    // --- libGDX InputProcessor callbacks on render thread ---

    @Override
    public boolean keyDown(int keycode) {
        if (keycode >= 0 && keycode < MAX_KEY_CODE) {
            rawQueue.enqueue(keycode, true);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        if (keycode >= 0 && keycode < MAX_KEY_CODE) {
            rawQueue.enqueue(keycode, false);
            return true;
        }
        return false;
    }

    // --- Per-tick simulation advance ---

    /**
     * Drains events inside a simulation tick per AGENTS.md section 7.4.
     * @param currentTick Current tick index.
     * @param isFirstTickOfFrame True if this is the first tick in a multi-tick frame.
     */
    public void tick(long currentTick, boolean isFirstTickOfFrame) {
        if (!isFirstTickOfFrame) {
            // Catch-up ticks do not drain new raw events and must not repeat "pressed"
            state.clearTransientFlags();
            globalState.clearTransientFlags();
            return;
        }

        state.beginTick();
        globalState.beginTick();

        // Reset per-tick single transition flags
        for (int i = 0; i < transitionedThisTick.length; i++) {
            transitionedThisTick[i] = false;
        }

        String activeContext = contextStack.getActiveContext();
        boolean externalCaptured = captureSource.wantsKeyboard();

        while (!rawQueue.isEmpty()) {
            int keycode = rawQueue.peekKeyCode();
            boolean isDown = rawQueue.peekDown();

            if (keycode >= 0 && keycode < MAX_KEY_CODE) {
                // Rule: At most one transition per button per tick.
                // If this key already transitioned this tick, leave event queued for next tick.
                if (transitionedThisTick[keycode]) {
                    break;
                }

                // Dequeue event
                rawQueue.dequeue();
                transitionedThisTick[keycode] = true;
                physicallyHeldKeys[keycode] = isDown;

                if (!isDown) {
                    // Physical release immediately clears swallowed state
                    swallowedKeys[keycode] = false;
                }

                // 1. Process global bindings (always active, independent of context)
                String globalAction = bindings.getActionForKey(InputContext.GLOBAL, keycode);
                if (globalAction != null) {
                    globalState.setButtonDown(globalAction, isDown);
                }

                // 2. Process active context bindings (if not swallowed or externally captured)
                if (!swallowedKeys[keycode] && !externalCaptured) {
                    String action = bindings.getActionForKey(activeContext, keycode);
                    if (action != null) {
                        state.setButtonDown(action, isDown);
                    }
                }
            } else {
                rawQueue.dequeue(); // Drop invalid out-of-range keycode
            }
        }

        state.resolveAxes();
        globalState.resolveAxes();
    }

    private void onContextChanged(String newContext) {
        // Clear all transient pressed/released flags on context change
        state.clearTransientFlags();
        state.clearAll();

        // Keys physically held across the change are swallowed
        for (int key = 0; key < MAX_KEY_CODE; key++) {
            if (physicallyHeldKeys[key]) {
                swallowedKeys[key] = true;
            }
        }
    }

    public InputState getState() {
        return state;
    }

    public InputState getGlobalState() {
        return globalState;
    }

    public InputContextStack getContextStack() {
        return contextStack;
    }

    public RawInputQueue getRawQueue() {
        return rawQueue;
    }

    public boolean isKeySwallowed(int keycode) {
        return keycode >= 0 && keycode < MAX_KEY_CODE && swallowedKeys[keycode];
    }
}
