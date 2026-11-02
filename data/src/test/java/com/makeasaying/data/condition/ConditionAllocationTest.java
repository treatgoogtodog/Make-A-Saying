package com.makeasaying.data.condition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeasaying.engine.condition.AtomRegistry;
import com.makeasaying.engine.condition.CompiledCondition;
import com.makeasaying.engine.flags.FlagDefinition;
import com.makeasaying.engine.flags.FlagRegistry;
import com.makeasaying.engine.flags.FlagScope;
import com.makeasaying.engine.flags.FlagStore;
import com.makeasaying.engine.flags.FlagType;
import com.makeasaying.engine.world.RoomStateStore;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionAllocationTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int ITERATIONS = 100000;

    @Test
    void testZeroAllocationDuringConditionEvaluation() throws Exception {
        java.lang.management.ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (!(threadBean instanceof com.sun.management.ThreadMXBean sunThreadBean) ||
            !sunThreadBean.isThreadAllocatedMemorySupported()) {
            return;
        }

        FlagDefinition fStage = new FlagDefinition(0, "test.stage", FlagType.INT, FlagScope.GLOBAL, 0, 0, 10, null, "doc");
        FlagDefinition fDoor = new FlagDefinition(1, "test.door_open", FlagType.BOOL, FlagScope.ROOM, 0, 0, 1, null, "doc");
        FlagDefinition fLever = new FlagDefinition(2, "test.lever_state", FlagType.ENUM, FlagScope.SESSION, 0, 0, 2, List.of("off", "half", "on"), "doc");

        FlagRegistry registry = new FlagRegistry(List.of(fStage, fDoor, fLever));
        AtomRegistry atomRegistry = new AtomRegistry();
        FlagStore store = new FlagStore(registry, new RoomStateStore());

        String json = """
        {
          "all": [
            { "flag": "test.stage", "op": ">=", "value": 0 },
            {
              "any": [
                { "flag": "test.door_open", "op": "==", "value": true },
                { "not": { "flag": "test.lever_state", "op": "==", "value": "on" } }
              ]
            }
          ]
        }
        """;
        CompiledCondition condition = ConditionLoader.parse(MAPPER.readTree(json), registry, atomRegistry);

        long threadId = Thread.currentThread().threadId();
        sunThreadBean.getThreadAllocatedBytes(threadId); // Prime ThreadMXBean

        // Warm up with 200k evaluations for C2 compilation
        boolean sink = false;
        for (int i = 0; i < 200000; i++) {
            sink ^= condition.evaluate(store, "room_01");
        }

        // Window 1: 100k iterations
        long memBefore1 = sunThreadBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < ITERATIONS; i++) {
            sink ^= condition.evaluate(store, "room_01");
        }
        long memAfter1 = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocated1 = memAfter1 - memBefore1;

        // Window 2: 100k iterations
        long memBefore2 = sunThreadBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < ITERATIONS; i++) {
            sink ^= condition.evaluate(store, "room_01");
        }
        long memAfter2 = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocated2 = memAfter2 - memBefore2;

        System.out.println("Condition evaluation window 1 (100k): " + allocated1 + " bytes, window 2 (100k): " + allocated2 + " bytes (sink=" + sink + ")");

        assertEquals(0, allocated1, "Condition evaluation window 1 must be strictly 0 bytes (was " + allocated1 + " bytes)");
        assertEquals(0, allocated2, "Condition evaluation window 2 must be strictly 0 bytes (was " + allocated2 + " bytes)");
    }

    @Test
    void testRoomFlagReadAndWriteAllocationSeparately() {
        java.lang.management.ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (!(threadBean instanceof com.sun.management.ThreadMXBean sunThreadBean) ||
            !sunThreadBean.isThreadAllocatedMemorySupported()) {
            return;
        }

        FlagDefinition fDoor = new FlagDefinition(0, "test.door_open", FlagType.BOOL, FlagScope.ROOM, 0, 0, 1, null, "doc");
        FlagRegistry registry = new FlagRegistry(List.of(fDoor));
        RoomStateStore roomStateStore = new RoomStateStore();
        FlagStore store = new FlagStore(registry, roomStateStore);

        long threadId = Thread.currentThread().threadId();
        sunThreadBean.getThreadAllocatedBytes(threadId);

        // 1. Measure Room Flag Reads: must be strictly 0 bytes
        int readSink = 0;
        for (int i = 0; i < 300000; i++) {
            readSink += store.get(0, "room_01");
        }

        long readMemBefore1 = sunThreadBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < ITERATIONS; i++) {
            readSink += store.get(0, "room_01");
        }
        long readMemAfter1 = sunThreadBean.getThreadAllocatedBytes(threadId);
        long readAllocated1 = readMemAfter1 - readMemBefore1;

        long readMemBefore2 = sunThreadBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < ITERATIONS; i++) {
            readSink += store.get(0, "room_01");
        }
        long readMemAfter2 = sunThreadBean.getThreadAllocatedBytes(threadId);
        long readAllocated2 = readMemAfter2 - readMemBefore2;

        System.out.println("Room-flag READ window 1 (100k): " + readAllocated1 + " bytes, window 2 (100k): " + readAllocated2 + " bytes (sink=" + readSink + ")");
        assertEquals(0, readAllocated1, "Room-flag read path must be strictly 0 bytes");
        assertEquals(0, readAllocated2, "Room-flag read path must be strictly 0 bytes");

        // 2. Measure Room Flag Writes: RoomStateStore creates Entry objects on store mutations
        long writeMemBefore = sunThreadBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < 10000; i++) {
            store.set(0, (i % 2 == 0) ? 1 : 0, com.makeasaying.engine.flags.FlagChangeLogEntry.SOURCE_GAMEPLAY);
            store.applyPendingChanges("room_01", null, i);
        }
        long writeMemAfter = sunThreadBean.getThreadAllocatedBytes(threadId);
        long writeAllocated = writeMemAfter - writeMemBefore;
        System.out.println("Room-flag WRITE (10k iterations): " + writeAllocated + " bytes total (" + (writeAllocated / 10000) + " bytes/write - known RoomStateStore.Entry cost)");
        assertTrue(writeAllocated > 0, "Room-flag writes allocate RoomStateStore.Entry objects as expected");
    }
}
