package com.makeasaying.engine.input.replay;

import java.util.ArrayList;
import java.util.List;

/**
 * Replay data structure per AGENTS.md section 7.4 and M2 brief.
 * Sparse record of held action changes and context transitions.
 */
public record ReplayData(
    int version,
    int tickRate,
    long initialSeed,
    String bindingsHash,
    List<ReplayFrame> frames
) {
    public static final int CURRENT_VERSION = 1;

    public record ReplayFrame(
        long tick,
        String context,
        List<String> down
    ) {
        public ReplayFrame {
            if (down == null) {
                down = new ArrayList<>();
            }
        }
    }
}
