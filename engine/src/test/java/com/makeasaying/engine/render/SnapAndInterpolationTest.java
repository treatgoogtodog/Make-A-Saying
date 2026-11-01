package com.makeasaying.engine.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SnapAndInterpolationTest {

    @Test
    void testSnapPositiveValues() {
        assertEquals(0, Snap.toPixel(0.0f));
        assertEquals(0, Snap.toPixel(0.2f));
        assertEquals(0, Snap.toPixel(0.49f));
        assertEquals(1, Snap.toPixel(0.5f));
        assertEquals(1, Snap.toPixel(0.51f));
        assertEquals(1, Snap.toPixel(0.9f));
        assertEquals(1, Snap.toPixel(1.0f));
        assertEquals(1, Snap.toPixel(1.4f));
        assertEquals(2, Snap.toPixel(1.5f));
    }

    @Test
    void testSnapNegativeValues() {
        assertEquals(0, Snap.toPixel(-0.0f));
        assertEquals(0, Snap.toPixel(-0.2f));
        assertEquals(0, Snap.toPixel(-0.49f));
        assertEquals(-1, Snap.toPixel(-0.5f));
        assertEquals(-1, Snap.toPixel(-0.51f));
        assertEquals(-1, Snap.toPixel(-0.9f));
        assertEquals(-1, Snap.toPixel(-1.0f));
        assertEquals(-1, Snap.toPixel(-1.4f));
        assertEquals(-2, Snap.toPixel(-1.5f));
    }

    @Test
    void testInterpolationAndSnapping() {
        // Lerp from 0 to 10
        assertEquals(0, Snap.lerpAndSnap(0f, 10f, 0.0f));
        assertEquals(3, Snap.lerpAndSnap(0f, 10f, 0.25f)); // 2.5 -> 3
        assertEquals(5, Snap.lerpAndSnap(0f, 10f, 0.5f));  // 5.0 -> 5
        assertEquals(8, Snap.lerpAndSnap(0f, 10f, 0.75f)); // 7.5 -> 8
        assertEquals(10, Snap.lerpAndSnap(0f, 10f, 1.0f));
    }

    @Test
    void testPositionStateStepAndReset() {
        PositionState pos = new PositionState(10f, 20f);
        assertEquals(10f, pos.getPrevX());
        assertEquals(10f, pos.getCurrX());

        // Step forward
        pos.step(20f, 40f);
        assertEquals(10f, pos.getPrevX());
        assertEquals(20f, pos.getCurrX());

        // Sub-tick interpolation
        assertEquals(15, pos.getSnappedX(0.5f));
        assertEquals(30, pos.getSnappedY(0.5f));

        // Teleport / spawn reset: must clear interpolation smear
        pos.reset(100f, 200f);
        assertEquals(100f, pos.getPrevX());
        assertEquals(100f, pos.getCurrX());
        assertEquals(200f, pos.getPrevY());
        assertEquals(200f, pos.getCurrY());

        // Alpha at 0.5 should now remain exactly at reset position (no smear)
        assertEquals(100, pos.getSnappedX(0.5f));
        assertEquals(200, pos.getSnappedY(0.5f));
    }
}
