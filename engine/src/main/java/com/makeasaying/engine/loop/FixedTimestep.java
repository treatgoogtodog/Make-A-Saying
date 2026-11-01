package com.makeasaying.engine.loop;

import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

/**
 * Pure fixed-timestep accumulator per AGENTS.md section 4.3 and M1 brief.
 * Holds no libGDX references. Simulation code never sees frame delta.
 */
public final class FixedTimestep {
    public static final float MAX_DELTA = 0.1f; // 100ms clamp to prevent spiral of death
    public static final int MAX_TICKS_PER_FRAME = 5;

    private final double tickDt;
    private final int tickRate;

    private double accumulator = 0.0;
    private long currentTick = 0L;
    private float alpha = 0f;

    private boolean paused = false;
    private boolean stepRequested = false;

    // Rate-limited warning tracking (measured in ticks or frame calls)
    private long lastWarningTick = -100L;

    public FixedTimestep(int tickRate) {
        if (tickRate <= 0) {
            throw new IllegalArgumentException("Tick rate must be positive: " + tickRate);
        }
        this.tickRate = tickRate;
        this.tickDt = 1.0 / tickRate;
    }

    public FixedTimestep() {
        this(60);
    }

    /**
     * Advances the accumulator by the given frame delta.
     * @param frameDelta Wall-clock frame delta in seconds.
     * @return Number of simulation ticks to execute this frame.
     */
    public int advance(float frameDelta) {
        if (paused) {
            if (stepRequested) {
                stepRequested = false;
                currentTick++;
                alpha = 0f;
                return 1;
            }
            alpha = 0f;
            return 0;
        }

        // Clamp frame delta to prevent huge jumps
        double clampedDelta = Math.max(0.0, Math.min((double) frameDelta, MAX_DELTA));
        accumulator += clampedDelta;

        int ticks = (int) (accumulator / tickDt);

        if (ticks > MAX_TICKS_PER_FRAME) {
            if (currentTick - lastWarningTick >= tickRate) {
                Log.warn(LogCategory.ENGINE,
                    "Accumulator overflow: requested %d ticks (cap is %d). Dropping excess time.",
                    ticks, MAX_TICKS_PER_FRAME);
                lastWarningTick = currentTick;
            }
            ticks = MAX_TICKS_PER_FRAME;
            // Drop excess time completely to prevent spiral of death
            accumulator = 0.0;
            alpha = 0f;
        } else {
            accumulator -= ticks * tickDt;
            // Guard floating-point inaccuracies so alpha is strictly in [0.0, 1.0)
            double rawAlpha = accumulator / tickDt;
            alpha = (float) Math.max(0.0, Math.min(rawAlpha, 0.999999));
        }

        currentTick += ticks;
        return ticks;
    }

    public void discardAccumulator() {
        accumulator = 0.0;
        alpha = 0f;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        if (paused) {
            accumulator = 0.0;
            alpha = 0f;
        }
    }

    public boolean isPaused() {
        return paused;
    }

    public void stepOnce() {
        if (paused) {
            stepRequested = true;
        }
    }

    public float getAlpha() {
        return alpha;
    }

    public long getCurrentTick() {
        return currentTick;
    }

    public int getTickRate() {
        return tickRate;
    }

    public float getTickDt() {
        return (float) tickDt;
    }

    public void reset() {
        accumulator = 0.0;
        currentTick = 0L;
        alpha = 0f;
        paused = false;
        stepRequested = false;
        lastWarningTick = -100L;
    }
}
