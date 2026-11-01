package com.makeasaying.data.tuning;

import com.makeasaying.engine.async.Jobs;
import com.makeasaying.engine.entity.MovementTuning;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MovementTuningWatcherTest {
    private Jobs jobs;

    @BeforeEach
    void setUp() {
        jobs = new Jobs(2);
    }

    @AfterEach
    void tearDown() {
        jobs.shutdown();
    }

    @Test
    void testInvalidFileKeepsOldValues(@TempDir Path tempDir) throws Exception {
        Path tuningFile = tempDir.resolve("movement.json");
        String validJson = """
        {
          "version": 1,
          "groundAccel": 900.0,
          "groundFriction": 1200.0,
          "groundTurnaroundAccel": 1800.0,
          "airAccel": 600.0,
          "airFriction": 300.0,
          "airTurnaroundAccel": 1200.0,
          "maxRunSpeed": 120.0,
          "jumpSpeed": 240.0,
          "jumpCutMultiplier": 0.5,
          "riseGravity": 900.0,
          "fallGravity": 1200.0,
          "maxFallSpeed": 360.0,
          "coyoteTicks": 6,
          "jumpBufferTicks": 6,
          "cornerCorrectionPx": 4,
          "slopeSnapPx": 6,
          "dropThroughTicks": 8
        }
        """;
        Files.writeString(tuningFile, validJson);

        MovementTuning initialTuning = MovementTuningLoader.load(tuningFile, 60);
        AtomicReference<MovementTuning> currentTuning = new AtomicReference<>(initialTuning);

        MovementTuningWatcher watcher = new MovementTuningWatcher(tuningFile, 60, jobs, currentTuning::set);

        // Write corrupt/invalid tuning (negative speed)
        String invalidJson = """
        {
          "version": 1,
          "maxRunSpeed": -50.0
        }
        """;
        // Ensure modified time advances
        Files.setLastModifiedTime(tuningFile, FileTime.from(Instant.now().plusSeconds(2)));
        Files.writeString(tuningFile, invalidJson);

        watcher.poll();

        // Wait up to 2 seconds for worker thread to process
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            jobs.drain();
            Thread.sleep(10);
        }

        // Must still hold initial tuning; invalid file kept old values
        assertEquals(initialTuning, currentTuning.get(), "Corrupt or invalid tuning file must keep previous values");
    }

    @Test
    void testValuesApplyOnlyAtDrainBoundary(@TempDir Path tempDir) throws Exception {
        Path tuningFile = tempDir.resolve("movement.json");
        String initialJson = """
        {
          "version": 1,
          "groundAccel": 900.0,
          "groundFriction": 1200.0,
          "groundTurnaroundAccel": 1800.0,
          "airAccel": 600.0,
          "airFriction": 300.0,
          "airTurnaroundAccel": 1200.0,
          "maxRunSpeed": 120.0,
          "jumpSpeed": 240.0,
          "jumpCutMultiplier": 0.5,
          "riseGravity": 900.0,
          "fallGravity": 1200.0,
          "maxFallSpeed": 360.0,
          "coyoteTicks": 6,
          "jumpBufferTicks": 6,
          "cornerCorrectionPx": 4,
          "slopeSnapPx": 6,
          "dropThroughTicks": 8
        }
        """;
        Files.writeString(tuningFile, initialJson);

        MovementTuning initialTuning = MovementTuningLoader.load(tuningFile, 60);
        AtomicReference<MovementTuning> activeTuning = new AtomicReference<>(initialTuning);

        MovementTuningWatcher watcher = new MovementTuningWatcher(tuningFile, 60, jobs, activeTuning::set);

        // Update file with new valid speed
        String updatedJson = initialJson.replace("\"maxRunSpeed\": 120.0", "\"maxRunSpeed\": 200.0");
        Files.setLastModifiedTime(tuningFile, FileTime.from(Instant.now().plusSeconds(2)));
        Files.writeString(tuningFile, updatedJson);

        watcher.poll();

        // Wait until worker thread has queued the result onto the main thread drain queue
        long deadline = System.currentTimeMillis() + 2000;
        while (jobs.getPendingMainThreadCount() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(jobs.getPendingMainThreadCount() > 0, "Worker job should have completed and queued main thread callback");

        // Before drain, values must NOT have been applied to simulation
        assertEquals(120.0f, activeTuning.get().maxRunSpeed(), "Tuning must not be applied mid-simulation before main-thread drain");

        // Now drain at simulated tick boundary
        jobs.drain();

        // After drain, updated tuning is applied
        assertEquals(200.0f, activeTuning.get().maxRunSpeed(), "Updated tuning must be applied after drain at tick boundary");
    }
}
