package com.makeasaying.engine.input.replay;

import com.makeasaying.engine.input.InputState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Records sparse action transitions and context changes across ticks.
 */
public final class InputRecorder {
    private final int tickRate;
    private final long initialSeed;
    private final String bindingsHash;

    private final List<ReplayData.ReplayFrame> frames = new ArrayList<>();
    private final Set<String> lastHeldActions = new HashSet<>();
    private String lastContext = "";
    private boolean recording = false;

    public InputRecorder(int tickRate, long initialSeed, String bindingsHash) {
        this.tickRate = tickRate;
        this.initialSeed = initialSeed;
        this.bindingsHash = bindingsHash != null ? bindingsHash : "default_hash";
    }

    public void start() {
        recording = true;
        frames.clear();
        lastHeldActions.clear();
        lastContext = "";
    }

    public void stop() {
        recording = false;
    }

    public boolean isRecording() {
        return recording;
    }

    public void recordTick(long currentTick, String currentContext, InputState state) {
        if (!recording) return;

        List<String> currentHeld = state.getHeldActionIds();
        Set<String> currentHeldSet = new HashSet<>(currentHeld);

        boolean actionsChanged = !currentHeldSet.equals(lastHeldActions);
        boolean contextChanged = !currentContext.equals(lastContext);

        if (actionsChanged || contextChanged || currentTick == 0) {
            Collections.sort(currentHeld);
            frames.add(new ReplayData.ReplayFrame(
                currentTick,
                contextChanged ? currentContext : null,
                new ArrayList<>(currentHeld)
            ));

            lastHeldActions.clear();
            lastHeldActions.addAll(currentHeldSet);
            lastContext = currentContext;
        }
    }

    public ReplayData toReplayData() {
        return new ReplayData(
            ReplayData.CURRENT_VERSION,
            tickRate,
            initialSeed,
            bindingsHash,
            new ArrayList<>(frames)
        );
    }
}
