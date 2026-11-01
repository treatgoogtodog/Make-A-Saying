package com.makeasaying.engine.async;

import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;
import com.makeasaying.engine.util.LogEntry;
import com.makeasaying.engine.util.LogLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JobsTest {
    private Jobs jobs;

    @BeforeEach
    void setUp() {
        Log.clear();
        jobs = new Jobs(2);
    }

    @AfterEach
    void tearDown() {
        if (jobs != null) {
            jobs.shutdown();
        }
    }

    @Test
    void testResultDeliveredOnMainThreadDrain() throws InterruptedException {
        AtomicReference<String> resultHolder = new AtomicReference<>();

        jobs.submit(() -> "SUCCESS_DATA", resultHolder::set, null);

        // Wait until background worker places result on mainThreadQueue
        long deadline = System.currentTimeMillis() + 2000;
        while (jobs.getPendingMainThreadCount() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(jobs.getPendingMainThreadCount() > 0, "Worker should have queued result for main thread");

        // Result should NOT be delivered until main thread drains
        assertNull(resultHolder.get());

        // Drain on main thread
        jobs.drain();
        assertEquals("SUCCESS_DATA", resultHolder.get());
    }

    @Test
    void testExceptionDispatchedToOnError() throws InterruptedException {
        AtomicReference<Throwable> errorHolder = new AtomicReference<>();

        jobs.submit(() -> {
            throw new IllegalStateException("Simulated disk error");
        }, ignored -> fail("onDone should not be called"), errorHolder::set);

        long deadline = System.currentTimeMillis() + 2000;
        while (jobs.getPendingMainThreadCount() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(jobs.getPendingMainThreadCount() > 0, "Worker should have queued error for main thread");
        assertNull(errorHolder.get());

        jobs.drain();
        assertNotNull(errorHolder.get());
        assertInstanceOf(IllegalStateException.class, errorHolder.get());
        assertEquals("Simulated disk error", errorHolder.get().getMessage());
    }

    @Test
    void testExceptionInsideOnDoneIsCaughtAndLogged() throws InterruptedException {
        jobs.submit(() -> 42, val -> {
            throw new RuntimeException("Callback bug in onDone");
        }, null);

        long deadline = System.currentTimeMillis() + 2000;
        while (jobs.getPendingMainThreadCount() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(jobs.getPendingMainThreadCount() > 0, "Worker should have queued task for main thread");

        // Drain must not throw exception
        assertDoesNotThrow(() -> jobs.drain());

        // Verify loud error was logged
        List<LogEntry> entries = Log.getEntries();
        boolean foundError = entries.stream().anyMatch(e ->
            e.level() == LogLevel.ERROR &&
            e.category() == LogCategory.ENGINE &&
            e.message().contains("Exception inside Jobs onDone callback"));
        assertTrue(foundError, "Exception inside onDone must be logged under ENGINE");
    }

    @Test
    void testSubmitAfterShutdownIsLoggedNotThrown() {
        jobs.shutdown();
        assertTrue(jobs.isShutdown());

        AtomicBoolean ran = new AtomicBoolean(false);
        assertDoesNotThrow(() ->
            jobs.submit(() -> ran.set(true), null, null)
        );

        jobs.drain();
        assertFalse(ran.get());

        List<LogEntry> entries = Log.getEntries();
        boolean foundWarning = entries.stream().anyMatch(e ->
            e.level() == LogLevel.WARN &&
            e.category() == LogCategory.ENGINE &&
            e.message().contains("Rejected job submission"));
        assertTrue(foundWarning, "Shutdown rejection must be logged as a warning");
    }
}
