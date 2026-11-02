package com.makeasaying.data.save;

import com.makeasaying.engine.async.Jobs;
import com.makeasaying.engine.flags.FlagDefinition;
import com.makeasaying.engine.flags.FlagRegistry;
import com.makeasaying.engine.flags.FlagScope;
import com.makeasaying.engine.flags.FlagType;
import com.makeasaying.engine.save.SaveClock;
import com.makeasaying.engine.save.SaveEnvelope;
import com.makeasaying.engine.save.SavePayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SafeSaveWriterTest {
    private FlagRegistry registry;
    private MemorySaveLocation location;
    private Jobs jobs;
    private SaveManager manager;

    @BeforeEach
    void setUp() {
        FlagDefinition fStage = new FlagDefinition(0, "test.stage", FlagType.INT, FlagScope.GLOBAL, 0, 0, 10, null, "doc");
        registry = new FlagRegistry(List.of(fStage));
        location = new MemorySaveLocation();
        jobs = new Jobs(2);
        manager = new SaveManager(location, registry, jobs, SaveClock.fixed("2026-11-02T08:00:00Z"));
    }

    @AfterEach
    void tearDown() {
        jobs.shutdown();
    }

    private void drainJobsUntilComplete() {
        while (manager.isWriteInFlight()) {
            jobs.drain();
            try {
                Thread.sleep(5);
            } catch (InterruptedException ignored) {}
        }
        jobs.drain();
    }

    @Test
    void testBackupRuleValidMainCopiedToBak() {
        // 1. Initial save: v1 with playtime 100
        SavePayload p1 = new SavePayload(100L, "2026-11-02T08:00:00Z", "test_01", "savepoint_1",
            Map.of("test.stage", 1), Collections.emptyMap(), Collections.emptyMap());
        manager.requestSave("slot_1", p1, null);
        drainJobsUntilComplete();

        assertTrue(location.existsMain("slot_1"));
        assertFalse(location.existsBak("slot_1"), "First save should not have a backup yet");

        // 2. Second save: v2 with playtime 200
        SavePayload p2 = new SavePayload(200L, "2026-11-02T08:00:00Z", "test_01", "savepoint_1",
            Map.of("test.stage", 2), Collections.emptyMap(), Collections.emptyMap());
        manager.requestSave("slot_1", p2, null);
        drainJobsUntilComplete();

        // Main now has p2, .bak has p1
        assertTrue(location.existsMain("slot_1"));
        assertTrue(location.existsBak("slot_1"));

        SaveEnvelope mainEnv = SaveLoader.load(location.getRaw("slot_1.json"), registry);
        assertEquals(200L, mainEnv.data().playtimeTicks());

        SaveEnvelope bakEnv = SaveLoader.load(location.getRaw("slot_1.bak"), registry);
        assertEquals(100L, bakEnv.data().playtimeTicks());
    }

    @Test
    void testCorruptMainNeverOverwritesBakAndQuarantines() {
        // Seed valid .bak
        SavePayload goodBak = new SavePayload(50L, "2026-11-02T08:00:00Z", "test_01", "savepoint_1",
            Map.of("test.stage", 0), Collections.emptyMap(), Collections.emptyMap());
        location.putRaw("slot_1.bak", SaveWriter.serialize(goodBak, registry));

        // Corrupt main file
        location.putRaw("slot_1.json", "{ malformed json corrupt content");

        // Write new save
        SavePayload pNew = new SavePayload(300L, "2026-11-02T08:00:00Z", "test_01", "savepoint_1",
            Map.of("test.stage", 3), Collections.emptyMap(), Collections.emptyMap());
        manager.requestSave("slot_1", pNew, null);
        drainJobsUntilComplete();

        // .bak must still contain the valid 50L save
        SaveEnvelope bakEnv = SaveLoader.load(location.getRaw("slot_1.bak"), registry);
        assertEquals(50L, bakEnv.data().playtimeTicks());

        // Corrupt main was moved to slot_1.corrupt
        assertEquals("{ malformed json corrupt content", location.getRaw("slot_1.corrupt"));

        // New main is written cleanly
        SaveEnvelope mainEnv = SaveLoader.load(location.getRaw("slot_1.json"), registry);
        assertEquals(300L, mainEnv.data().playtimeTicks());
    }

    @Test
    void testTwoConsecutiveCorruptionsCreateUniqueQuarantineNames() {
        // Pre-create slot_1.corrupt
        location.putRaw("slot_1.corrupt", "first corrupt file");

        // Now place a second corrupt main file
        location.putRaw("slot_1.json", "second corrupt file");

        // Request save
        SavePayload p = new SavePayload(123L, "2026-11-02T08:00:00Z", "test_01", "savepoint_1",
            Map.of("test.stage", 1), Collections.emptyMap(), Collections.emptyMap());
        manager.requestSave("slot_1", p, null);
        drainJobsUntilComplete();

        // First corrupt file preserved
        assertEquals("first corrupt file", location.getRaw("slot_1.corrupt"));
        // Second corrupt file quarantined as slot_1.corrupt.1
        assertEquals("second corrupt file", location.getRaw("slot_1.corrupt.1"));
        // Main file written cleanly
        assertTrue(location.existsMain("slot_1"));
    }

    @Test
    void testCrashSimulationAfterTempWrite() {
        // Initial valid save
        SavePayload p1 = new SavePayload(100L, "2026-11-02T08:00:00Z", "test_01", "savepoint_1",
            Map.of("test.stage", 1), Collections.emptyMap(), Collections.emptyMap());
        manager.requestSave("slot_1", p1, null);
        drainJobsUntilComplete();

        // Simulate crash right before atomic rename
        location.setCrashBeforeRename(true);

        SavePayload p2 = new SavePayload(200L, "2026-11-02T08:00:00Z", "test_01", "savepoint_1",
            Map.of("test.stage", 2), Collections.emptyMap(), Collections.emptyMap());
        manager.requestSave("slot_1", p2, null);
        drainJobsUntilComplete();

        // p1 main save remains 100% valid and untruncated!
        SaveEnvelope mainEnv = SaveLoader.load(location.getRaw("slot_1.json"), registry);
        assertEquals(100L, mainEnv.data().playtimeTicks());
    }

    @Test
    void testCrashSimulationMidWriteTruncation() {
        // Initial valid save
        SavePayload p1 = new SavePayload(100L, "2026-11-02T08:00:00Z", "test_01", "savepoint_1",
            Map.of("test.stage", 1), Collections.emptyMap(), Collections.emptyMap());
        manager.requestSave("slot_1", p1, null);
        drainJobsUntilComplete();

        // Simulate power outage / crash mid-write
        location.setCrashMidWrite(true);

        SavePayload p2 = new SavePayload(200L, "2026-11-02T08:00:00Z", "test_01", "savepoint_1",
            Map.of("test.stage", 2), Collections.emptyMap(), Collections.emptyMap());
        manager.requestSave("slot_1", p2, null);
        drainJobsUntilComplete();

        // p1 main save remains 100% valid
        SaveEnvelope mainEnv = SaveLoader.load(location.getRaw("slot_1.json"), registry);
        assertEquals(100L, mainEnv.data().playtimeTicks());
    }

    @Test
    void testFallbackLoadFromBakWhenMainCorrupt() {
        SavePayload goodBak = new SavePayload(50L, "2026-11-02T08:00:00Z", "test_01", "savepoint_1",
            Map.of("test.stage", 0), Collections.emptyMap(), Collections.emptyMap());
        location.putRaw("slot_1.bak", SaveWriter.serialize(goodBak, registry));

        // Corrupt main
        location.putRaw("slot_1.json", "{ corrupt payload");

        SaveEnvelope loaded = manager.loadSaveWithFallback("slot_1");
        assertNotNull(loaded, "Should fall back to valid .bak");
        assertEquals(50L, loaded.data().playtimeTicks());

        // Corrupt file was quarantined
        assertTrue(location.getFilesSnapshot().containsKey("slot_1.corrupt"));
    }

    @Test
    void testNewGameFallbackWhenBothCorrupt() {
        location.putRaw("slot_1.json", "{ corrupt main");
        location.putRaw("slot_1.bak", "{ corrupt bak");

        SaveEnvelope loaded = manager.loadSaveWithFallback("slot_1");
        assertNull(loaded, "When both main and bak are corrupt, returns null to start new game");
    }
}
