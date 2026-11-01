package com.makeasaying.engine.loop;

import com.makeasaying.engine.render.PositionState;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationAllocationTest {

    @Test
    void testZeroAllocationsInHotSimulationLoop() {
        java.lang.management.ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (!(threadBean instanceof com.sun.management.ThreadMXBean sunThreadBean)) {
            // JVM does not support thread allocated memory tracking on this platform
            return;
        }
        if (!sunThreadBean.isThreadAllocatedMemorySupported()) {
            return;
        }

        FixedTimestep timestep = new FixedTimestep(60);
        PositionState positionState = new PositionState(0f, 0f);
        FrameTimer frameTimer = new FrameTimer(60);

        long threadId = Thread.currentThread().threadId();

        // 1. Warm-up to trigger JIT compilation and static initializations
        for (int i = 0; i < 5000; i++) {
            int ticks = timestep.advance(0.0166667f);
            for (int t = 0; t < ticks; t++) {
                positionState.step(positionState.getCurrX() + 1.0f, positionState.getCurrY());
            }
            int x = positionState.getSnappedX(timestep.getAlpha());
            int y = positionState.getSnappedY(timestep.getAlpha());
            frameTimer.recordFrame(0.1f, 0.2f);
        }

        // 2. Measure allocated memory over 100,000 iterations
        long bytesBefore = sunThreadBean.getThreadAllocatedBytes(threadId);

        int iterations = 100_000;
        int dummyAccumulator = 0;
        for (int i = 0; i < iterations; i++) {
            int ticks = timestep.advance(0.0166667f);
            for (int t = 0; t < ticks; t++) {
                positionState.step(positionState.getCurrX() + 1.0f, positionState.getCurrY());
            }
            int x = positionState.getSnappedX(timestep.getAlpha());
            int y = positionState.getSnappedY(timestep.getAlpha());
            frameTimer.recordFrame(0.15f, 0.25f);
            dummyAccumulator += (x + y);
        }

        long bytesAfter = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocatedBytes = bytesAfter - bytesBefore;

        // Prevent JVM dead code elimination
        assertTrue(dummyAccumulator != 0 || dummyAccumulator == 0);

        // Over 100,000 frames of simulation, position stepping, snapping, and frame timing:
        // Memory allocated must be 0 (or near zero, e.g. < 100 bytes for MXBean internals)
        assertTrue(allocatedBytes < 1024,
            "Hot simulation loop must not allocate memory per frame! Allocated bytes across 100k frames: " + allocatedBytes);
    }
}
