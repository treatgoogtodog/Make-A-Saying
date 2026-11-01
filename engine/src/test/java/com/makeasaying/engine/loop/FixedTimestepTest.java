package com.makeasaying.engine.loop;

import com.makeasaying.engine.render.PositionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixedTimestepTest {
    private FixedTimestep timestep;

    @BeforeEach
    void setUp() {
        timestep = new FixedTimestep(60);
    }

    @Test
    void testZeroTicksInOneFrame() {
        // Delta less than one tick (1/60s ≈ 0.01667s)
        int ticks = timestep.advance(0.005f);
        assertEquals(0, ticks);
        assertEquals(0L, timestep.getCurrentTick());
        assertTrue(timestep.getAlpha() > 0f && timestep.getAlpha() < 1f);
    }

    @Test
    void testOneTickInOneFrame() {
        int ticks = timestep.advance(1.0f / 60.0f);
        assertEquals(1, ticks);
        assertEquals(1L, timestep.getCurrentTick());
        assertEquals(0f, timestep.getAlpha(), 0.001f);
    }

    @Test
    void testManyTicksInOneFrame() {
        int ticks = timestep.advance(3.0f / 60.0f);
        assertEquals(3, ticks);
        assertEquals(3L, timestep.getCurrentTick());
        assertEquals(0f, timestep.getAlpha(), 0.001f);
    }

    @Test
    void testFractionalCarryAcrossFrames() {
        float dt = 1.0f / 60.0f;
        int t1 = timestep.advance(dt * 0.6f);
        assertEquals(0, t1);
        assertEquals(0.6f, timestep.getAlpha(), 0.01f);

        int t2 = timestep.advance(dt * 0.6f); // Total 1.2 ticks
        assertEquals(1, t2);
        assertEquals(1L, timestep.getCurrentTick());
        assertEquals(0.2f, timestep.getAlpha(), 0.01f);
    }

    @Test
    void testAlphaAlwaysStrictlyBetweenZeroAndOne() {
        float[] deltas = {0.0001f, 0.00833f, 0.01666f, 0.025f, 0.03333f, 0.05f, 0.09999f};
        for (float delta : deltas) {
            timestep.advance(delta);
            float alpha = timestep.getAlpha();
            assertTrue(alpha >= 0.0f, "Alpha must be >= 0.0, was: " + alpha);
            assertTrue(alpha < 1.0f, "Alpha must be < 1.0, was: " + alpha);
        }
    }

    @Test
    void testDeltaClampAndTickCap() {
        // Feed 1.0 second delta (exceeds MAX_DELTA 0.1s and MAX_TICKS_PER_FRAME 5)
        int ticks = timestep.advance(1.0f);
        assertEquals(5, ticks);
        assertEquals(5L, timestep.getCurrentTick());
        assertEquals(0f, timestep.getAlpha());

        // Subsequent normal frame should not suffer from accumulated dropped time
        int nextTicks = timestep.advance(1.0f / 60.0f);
        assertEquals(1, nextTicks);
        assertEquals(6L, timestep.getCurrentTick());
    }

    @Test
    void testPauseAndStepOnce() {
        timestep.setPaused(true);
        assertTrue(timestep.isPaused());

        int ticksWhilePaused = timestep.advance(0.1f);
        assertEquals(0, ticksWhilePaused);
        assertEquals(0L, timestep.getCurrentTick());

        timestep.stepOnce();
        int stepTick = timestep.advance(0.1f);
        assertEquals(1, stepTick);
        assertEquals(1L, timestep.getCurrentTick());

        // Next advance is paused again
        int nextPaused = timestep.advance(0.1f);
        assertEquals(0, nextPaused);
        assertEquals(1L, timestep.getCurrentTick());
    }

    @Test
    void testSyntheticRefreshRateParity60_120_144Hz() {
        // Simulate a moving object advancing 1 pixel per tick over 1 second of wall clock
        // Under 60Hz, 120Hz, and 144Hz display rates:
        FixedTimestep ts60 = new FixedTimestep(60);
        FixedTimestep ts120 = new FixedTimestep(60);
        FixedTimestep ts144 = new FixedTimestep(60);

        PositionState pos60 = new PositionState(0f, 0f);
        PositionState pos120 = new PositionState(0f, 0f);
        PositionState pos144 = new PositionState(0f, 0f);

        // 60 frames at 1/60s
        for (int i = 0; i < 60; i++) {
            int ticks = ts60.advance(1.0f / 60.0f);
            for (int t = 0; t < ticks; t++) pos60.step(pos60.getCurrX() + 1.0f, 0f);
        }

        // 120 frames at 1/120s
        for (int i = 0; i < 120; i++) {
            int ticks = ts120.advance(1.0f / 120.0f);
            for (int t = 0; t < ticks; t++) pos120.step(pos120.getCurrX() + 1.0f, 0f);
        }

        // 144 frames at 1/144s
        for (int i = 0; i < 144; i++) {
            int ticks = ts144.advance(1.0f / 144.0f);
            for (int t = 0; t < ticks; t++) pos144.step(pos144.getCurrX() + 1.0f, 0f);
        }

        // All three refresh rates must yield identical rendered positions at 1.0 second elapsed
        int render60 = pos60.getSnappedX(ts60.getAlpha());
        int render120 = pos120.getSnappedX(ts120.getAlpha());
        int render144 = pos144.getSnappedX(ts144.getAlpha());

        assertEquals(render60, render120, "60Hz and 120Hz rendered positions must match");
        assertEquals(render60, render144, "60Hz and 144Hz rendered positions must match");

        // Exactly 60 ticks simulated across 1 second
        assertEquals(60L, ts60.getCurrentTick());
        assertEquals(60L, ts120.getCurrentTick());
        assertTrue(ts144.getCurrentTick() == 60L || ts144.getCurrentTick() == 59L);
    }
}
