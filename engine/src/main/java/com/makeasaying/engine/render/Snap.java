package com.makeasaying.engine.render;

/**
 * Pixel snapping and integer quantization helpers per AGENTS.md section 6 and M1 brief.
 */
public final class Snap {
    private Snap() {}

    /**
     * Symmetrically rounds float coordinates to whole integer pixels.
     * Round half away from zero: 0.5 -> 1, -0.5 -> -1.
     */
    public static int toPixel(float value) {
        if (value >= 0f) {
            return (int) (value + 0.5f);
        } else {
            return (int) (value - 0.5f);
        }
    }

    /**
     * Linearly interpolates between previous and current values by alpha, then snaps to whole pixel.
     */
    public static int lerpAndSnap(float previous, float current, float alpha) {
        float interpolated = previous + (current - previous) * alpha;
        return toPixel(interpolated);
    }

    /**
     * Linearly interpolates without snapping.
     */
    public static float lerp(float previous, float current, float alpha) {
        return previous + (current - previous) * alpha;
    }
}
