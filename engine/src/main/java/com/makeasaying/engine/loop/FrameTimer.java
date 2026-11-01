package com.makeasaying.engine.loop;

/**
 * Allocation-free frame time tracking ring buffer per AGENTS.md section 7.1 and M1 brief.
 * Records update and render times in milliseconds for window title stats and later ImGui integration.
 */
public final class FrameTimer {
    public static final int DEFAULT_SAMPLE_COUNT = 60;

    private final float[] updateSamples;
    private final float[] renderSamples;
    private final int capacity;

    private int index = 0;
    private int sampleCount = 0;

    private float updateSum = 0f;
    private float renderSum = 0f;

    private long lastTitleUpdateNanos = 0L;
    private float cachedAvgUpdate = 0f;
    private float cachedAvgRender = 0f;

    public FrameTimer(int capacity) {
        this.capacity = Math.max(10, capacity);
        this.updateSamples = new float[this.capacity];
        this.renderSamples = new float[this.capacity];
    }

    public FrameTimer() {
        this(DEFAULT_SAMPLE_COUNT);
    }

    /**
     * Records the frame's update and render duration in milliseconds without any heap allocation.
     */
    public void recordFrame(float updateMs, float renderMs) {
        if (sampleCount < capacity) {
            sampleCount++;
        } else {
            updateSum -= updateSamples[index];
            renderSum -= renderSamples[index];
        }

        updateSamples[index] = updateMs;
        renderSamples[index] = renderMs;
        updateSum += updateMs;
        renderSum += renderMs;

        index = (index + 1) % capacity;
    }

    public float getAverageUpdateMs() {
        return sampleCount > 0 ? updateSum / sampleCount : 0f;
    }

    public float getAverageRenderMs() {
        return sampleCount > 0 ? renderSum / sampleCount : 0f;
    }

    public float getAverageFrameMs() {
        return getAverageUpdateMs() + getAverageRenderMs();
    }

    public float getEstimatedFps() {
        float avgTotal = getAverageFrameMs();
        return avgTotal > 0.0001f ? 1000.0f / avgTotal : 0f;
    }

    /**
     * Checks if one second has elapsed since the last title update check.
     */
    public boolean shouldUpdateTitle(long nowNanos) {
        if (nowNanos - lastTitleUpdateNanos >= 1_000_000_000L) {
            lastTitleUpdateNanos = nowNanos;
            cachedAvgUpdate = getAverageUpdateMs();
            cachedAvgRender = getAverageRenderMs();
            return true;
        }
        return false;
    }

    public float getCachedAvgUpdate() {
        return cachedAvgUpdate;
    }

    public float getCachedAvgRender() {
        return cachedAvgRender;
    }
}
