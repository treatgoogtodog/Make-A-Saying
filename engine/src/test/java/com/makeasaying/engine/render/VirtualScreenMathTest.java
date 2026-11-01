package com.makeasaying.engine.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VirtualScreenMathTest {

    @Test
    void testStandardIntegerScales() {
        // 1280x720 gives 4x
        var calc1280 = VirtualScreenMath.calculate(320, 180, 1280, 720);
        assertEquals(4, calc1280.scale());
        assertEquals(1280, calc1280.viewportWidth());
        assertEquals(720, calc1280.viewportHeight());
        assertEquals(0, calc1280.viewportX());
        assertEquals(0, calc1280.viewportY());
        assertFalse(calc1280.cropped());

        // 1920x1080 gives 6x
        var calc1920 = VirtualScreenMath.calculate(320, 180, 1920, 1080);
        assertEquals(6, calc1920.scale());
        assertEquals(1920, calc1920.viewportWidth());
        assertEquals(1080, calc1920.viewportHeight());
        assertEquals(0, calc1920.viewportX());
        assertEquals(0, calc1920.viewportY());
        assertFalse(calc1920.cropped());

        // 2560x1440 gives 8x
        var calc2560 = VirtualScreenMath.calculate(320, 180, 2560, 1440);
        assertEquals(8, calc2560.scale());
        assertEquals(2560, calc2560.viewportWidth());
        assertEquals(1440, calc2560.viewportHeight());
        assertFalse(calc2560.cropped());

        // 3840x2160 gives 12x
        var calc3840 = VirtualScreenMath.calculate(320, 180, 3840, 2160);
        assertEquals(12, calc3840.scale());
        assertEquals(3840, calc3840.viewportWidth());
        assertEquals(2160, calc3840.viewportHeight());
        assertFalse(calc3840.cropped());
    }

    @Test
    void testLetterboxAndPillarboxBorders() {
        // 1000x700 gives 3x with borders
        // 320 * 3 = 960, 180 * 3 = 540
        var calc = VirtualScreenMath.calculate(320, 180, 1000, 700);
        assertEquals(3, calc.scale());
        assertEquals(960, calc.viewportWidth());
        assertEquals(540, calc.viewportHeight());
        assertEquals(20, calc.viewportX()); // (1000 - 960) / 2
        assertEquals(80, calc.viewportY()); // (700 - 540) / 2
        assertFalse(calc.cropped());
    }

    @Test
    void testUnderMinimumResolutionCropping() {
        // 300x100 gives 1x with cropping and warning
        var calc = VirtualScreenMath.calculate(320, 180, 300, 100);
        assertEquals(1, calc.scale());
        assertEquals(320, calc.viewportWidth());
        assertEquals(180, calc.viewportHeight());
        assertEquals(-10, calc.viewportX()); // (300 - 320) / 2
        assertEquals(-40, calc.viewportY()); // (100 - 180) / 2
        assertTrue(calc.cropped());
    }
}
