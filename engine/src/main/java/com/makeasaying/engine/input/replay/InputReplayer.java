package com.makeasaying.engine.input.replay;

import com.makeasaying.engine.input.ActionRegistry;
import com.makeasaying.engine.input.InputState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Replays recorded sparse action transitions, reconstructing deterministic per-tick InputState.
 */
public final class InputReplayer {
    private final ReplayData data;
    private final ActionRegistry registry;
    private final InputState state;

    private int nextFrameIndex = 0;
    private final Set<String> activeDownActions = new HashSet<>();
    private String currentContext = "";
    private boolean playing = false;

    public InputReplayer(ReplayData data, ActionRegistry registry) {
        this.data = data;
        this.registry = registry;
        this.state = new InputState(registry);
    }

    public void start() {
        playing = true;
        nextFrameIndex = 0;
        activeDownActions.clear();
        state.clearAll();
    }

    public void stop() {
        playing = false;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void tick(long currentTick) {
        if (!playing) return;

        state.beginTick();

        List<ReplayData.ReplayFrame> frames = data.frames();
        while (nextFrameIndex < frames.size() && frames.get(nextFrameIndex).tick() <= currentTick) {
            ReplayData.ReplayFrame frame = frames.get(nextFrameIndex);
            if (frame.context() != null) {
                currentContext = frame.context();
            }

            Set<String> newDown = new HashSet<>(frame.down());

            // Check for buttons released
            for (String held : new HashSet<>(activeDownActions)) {
                if (!newDown.contains(held)) {
                    state.setButtonDown(held, false);
                    activeDownActions.remove(held);
                }
            }

            // Check for buttons pressed
            for (String action : newDown) {
                if (!activeDownActions.contains(action)) {
                    state.setButtonDown(action, true);
                    activeDownActions.add(action);
                }
            }

            nextFrameIndex++;
        }

        state.resolveAxes();

        if (nextFrameIndex >= frames.size() && activeDownActions.isEmpty()) {
            playing = false;
        }
    }

    public InputState getState() {
        return state;
    }

    public String getCurrentContext() {
        return currentContext;
    }
}
