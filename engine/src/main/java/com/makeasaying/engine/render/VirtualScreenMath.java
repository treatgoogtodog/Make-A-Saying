package com.makeasaying.engine.render;

import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

/**
 * Pure math calculations for integer scaling and viewport centering.
 * No libGDX dependencies.
 */
public final class VirtualScreenMath {
    private VirtualScreenMath() {}

    public record ViewportCalculation(
        int scale,
        int viewportX,
        int viewportY,
        int viewportWidth,
        int viewportHeight,
        boolean cropped
    ) {}

    public static ViewportCalculation calculate(int virtualWidth, int virtualHeight, int windowWidth, int windowHeight) {
        boolean cropped = false;
        int scale;

        if (windowWidth < virtualWidth || windowHeight < virtualHeight) {
            scale = 1;
            cropped = true;
            Log.warn(LogCategory.RENDER,
                "Window backbuffer size %dx%d is smaller than virtual size %dx%d. Scale 1 cropping applied.",
                windowWidth, windowHeight, virtualWidth, virtualHeight);
        } else {
            scale = Math.max(1, Math.min(windowWidth / virtualWidth, windowHeight / virtualHeight));
        }

        int viewportWidth = virtualWidth * scale;
        int viewportHeight = virtualHeight * scale;
        int viewportX = (windowWidth - viewportWidth) / 2;
        int viewportY = (windowHeight - viewportHeight) / 2;

        return new ViewportCalculation(scale, viewportX, viewportY, viewportWidth, viewportHeight, cropped);
    }
}
