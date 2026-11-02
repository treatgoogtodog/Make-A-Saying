package com.makeasaying.engine.flags;

import com.makeasaying.engine.entity.events.EntityEvent;
import com.makeasaying.engine.entity.events.EntityEventQueue;
import com.makeasaying.engine.entity.events.EntityEventType;
import com.makeasaying.engine.world.RoomStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlagStoreTest {
    private FlagRegistry registry;
    private RoomStateStore roomStateStore;
    private FlagStore store;
    private EntityEventQueue eventQueue;

    @BeforeEach
    void setup() {
        FlagDefinition fGlobalInt = new FlagDefinition(0, "global.int", FlagType.INT, FlagScope.GLOBAL, 0, 0, 10, null, "doc");
        FlagDefinition fGlobalBool = new FlagDefinition(1, "global.bool", FlagType.BOOL, FlagScope.GLOBAL, 0, 0, 1, null, "doc");
        FlagDefinition fSessionEnum = new FlagDefinition(2, "session.enum", FlagType.ENUM, FlagScope.SESSION, 0, 0, 2, List.of("a", "b", "c"), "doc");
        FlagDefinition fRoomBool = new FlagDefinition(3, "room.bool", FlagType.BOOL, FlagScope.ROOM, 0, 0, 1, null, "doc");
        FlagDefinition fRoomTempBool = new FlagDefinition(4, "temp.bool", FlagType.BOOL, FlagScope.ROOM_TEMP, 1, 0, 1, null, "doc");

        registry = new FlagRegistry(List.of(fGlobalInt, fGlobalBool, fSessionEnum, fRoomBool, fRoomTempBool));
        roomStateStore = new RoomStateStore();
        store = new FlagStore(registry, roomStateStore);
        eventQueue = new EntityEventQueue();
    }

    @Test
    void testInitialDefaultValues() {
        assertEquals(0, store.get(0, "room_a"));
        assertEquals(0, store.get(1, "room_a"));
        assertEquals(0, store.get(2, "room_a"));
        assertEquals(0, store.get(3, "room_a"));
        assertEquals(1, store.get(4, "room_a"));
    }

    @Test
    void testPendingQueueOrderAndLastWriteWins() {
        // Queue multiple mutations for global.int in the same tick
        store.set(0, 2, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.add(0, 3, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.set(0, 7, FlagChangeLogEntry.SOURCE_GAMEPLAY);

        // Before tick boundary, reads see applied value (0)
        assertEquals(0, store.get(0, "room_a"));

        // Apply pending changes
        store.applyPendingChanges("room_a", eventQueue, 100L);

        // After boundary, last write wins
        assertEquals(7, store.get(0, "room_a"));

        // Verify events were emitted in queue order
        assertEquals(3, eventQueue.size());
        java.util.List<EntityEvent> received = new java.util.ArrayList<>();
        eventQueue.subscribe(ev -> received.add(new EntityEvent(ev.type(), ev.entityId(), ev.targetId(), ev.detail(), ev.extra())));
        eventQueue.dispatch();

        assertEquals(3, received.size());
        EntityEvent last = received.get(2);
        assertEquals(EntityEventType.FLAG_CHANGED, last.type());
        assertEquals(0, last.entityId());
        assertEquals(5, last.detail());
        assertEquals(7, last.extra());
    }

    @Test
    void testAddClampingAndOutOfRangeRejection() {
        store.set(0, 8, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.applyPendingChanges("room_a", eventQueue, 10L);
        assertEquals(8, store.get(0, "room_a"));

        // Adding 5 would exceed max (10), so it should be rejected
        store.add(0, 5, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.applyPendingChanges("room_a", eventQueue, 11L);
        assertEquals(8, store.get(0, "room_a"));

        // Adding -10 would go below min (0), so it should be rejected
        store.add(0, -10, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.applyPendingChanges("room_a", eventQueue, 12L);
        assertEquals(8, store.get(0, "room_a"));

        // Valid add +2 reaches 10
        store.add(0, 2, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.applyPendingChanges("room_a", eventQueue, 13L);
        assertEquals(10, store.get(0, "room_a"));
    }

    @Test
    void testToggleBoolAndRejectNonBool() {
        assertEquals(0, store.get(1, "room_a"));

        // Toggle bool 0 -> 1
        store.toggle(1, FlagChangeLogEntry.SOURCE_DEBUG_PANEL);
        store.applyPendingChanges("room_a", eventQueue, 20L);
        assertEquals(1, store.get(1, "room_a"));

        // Toggle bool 1 -> 0
        store.toggle(1, FlagChangeLogEntry.SOURCE_DEBUG_PANEL);
        store.applyPendingChanges("room_a", eventQueue, 21L);
        assertEquals(0, store.get(1, "room_a"));

        // Toggling non-bool (index 0) is ignored / logged
        store.toggle(0, FlagChangeLogEntry.SOURCE_DEBUG_PANEL);
        store.applyPendingChanges("room_a", eventQueue, 22L);
        assertEquals(0, store.get(0, "room_a"));
    }

    @Test
    void testRoomScopeIsolationAcrossRooms() {
        store.set(3, 1, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.applyPendingChanges("room_a", eventQueue, 30L);

        // In room_a, flag is 1
        assertEquals(1, store.get(3, "room_a"));

        // In room_b, flag remains default (0)
        assertEquals(0, store.get(3, "room_b"));

        // Set in room_b
        store.set(3, 1, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.applyPendingChanges("room_b", eventQueue, 31L);
        assertEquals(1, store.get(3, "room_b"));
    }

    @Test
    void testClearRoomTempOnRoomSwap() {
        // Mutate temp.bool from 1 to 0
        store.set(4, 0, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.applyPendingChanges("room_a", eventQueue, 40L);
        assertEquals(0, store.get(4, "room_a"));

        eventQueue.clear();

        // Perform room swap: silent reset
        store.clearRoomTemp(45L);
        assertEquals(1, store.get(4, "room_b"));

        // Silent clear does not emit events
        assertEquals(0, eventQueue.size());

        // But is logged with SOURCE_ROOM_SWAP
        assertTrue(store.getLogCount() > 0);
        FlagChangeLogEntry lastLog = store.getLogEntry(store.getLogCount() - 1);
        assertEquals(FlagChangeLogEntry.SOURCE_ROOM_SWAP, lastLog.source);
        assertEquals(4, lastLog.flagIndex);
        assertEquals(0, lastLog.oldValue);
        assertEquals(1, lastLog.newValue);
    }

    @Test
    void testSnapshotCaptureAndRestore() {
        store.set(0, 5, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.set(1, 1, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.set(2, 2, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.set(4, 0, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.applyPendingChanges("room_a", eventQueue, 50L);

        FlagStore.Snapshot snap = store.createSnapshot();

        // Mutate store further
        store.set(0, 9, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.set(1, 0, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.applyPendingChanges("room_a", eventQueue, 51L);
        assertEquals(9, store.get(0, "room_a"));
        assertEquals(0, store.get(1, "room_a"));

        // Restore snapshot
        store.restoreSnapshot(snap);
        assertEquals(5, store.get(0, "room_a"));
        assertEquals(1, store.get(1, "room_a"));
        assertEquals(2, store.get(2, "room_a"));
        assertEquals(0, store.get(4, "room_a"));
    }

    @Test
    void testStateHashChangesDeterministically() {
        long hash1 = store.computeStateHash("room_a");
        long hash2 = store.computeStateHash("room_a");
        assertEquals(hash1, hash2);

        store.set(0, 3, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.applyPendingChanges("room_a", eventQueue, 60L);

        long hash3 = store.computeStateHash("room_a");
        assertNotEquals(hash1, hash3);
    }

    @Test
    void testAddRejectionAtExactUpperAndLowerBounds() {
        // global.int min=0, max=10, initial=0
        assertEquals(0, store.get(0, "room_a"));

        // At lower bound (0), adding -1 is rejected, old value 0 stays
        store.add(0, -1, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.applyPendingChanges("room_a", eventQueue, 70L);
        assertEquals(0, store.get(0, "room_a"), "Add below lower bound must be rejected, keeping old value");

        // Set to upper bound (10)
        store.set(0, 10, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.applyPendingChanges("room_a", eventQueue, 71L);
        assertEquals(10, store.get(0, "room_a"));

        // At upper bound (10), adding +1 is rejected, old value 10 stays
        store.add(0, 1, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.applyPendingChanges("room_a", eventQueue, 72L);
        assertEquals(10, store.get(0, "room_a"), "Add above upper bound must be rejected, keeping old value");
    }

    @Test
    void testPendingBufferOverflowLoudlyDropsChange() {
        // Buffer capacity is 256. Enqueue 256 valid sets
        for (int i = 0; i < 256; i++) {
            store.set(0, i % 11, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        }

        // 257th write overflows the buffer and must be dropped
        store.set(0, 9, FlagChangeLogEntry.SOURCE_GAMEPLAY);

        // Apply all
        store.applyPendingChanges("room_a", eventQueue, 80L);

        // The 256th operation was (255 % 11) = 2, so final value is 2, not 9
        assertEquals(2, store.get(0, "room_a"));
    }

    @Test
    void testLogBufferWraparound() {
        // Change log ring buffer capacity is 256. Enqueue and apply 300 changes
        for (int i = 0; i < 300; i++) {
            store.set(0, (i % 2 == 0) ? 1 : 2, FlagChangeLogEntry.SOURCE_GAMEPLAY);
            store.applyPendingChanges("room_a", eventQueue, i + 100L);
        }

        // Log count should be clamped to buffer capacity 256
        assertEquals(256, store.getLogCount());

        // Latest log entry should correspond to tick 399L
        FlagChangeLogEntry latest = store.getLogEntry(255);
        assertEquals(399L, latest.tick);
    }

    @Test
    void testEventCascadeLimit() {
        EntityEventQueue queue = new EntityEventQueue(256);
        int[] receivedCount = new int[1];

        // Self-triggering listener that emits an event on every received event
        queue.subscribe(ev -> {
            receivedCount[0]++;
            queue.emitFlagChanged(0, 0, 1);
        });

        // Seed with 1 event
        queue.emitFlagChanged(0, 0, 1);

        // Dispatch must halt at CASCADE_LIMIT (500)
        queue.dispatch();

        assertEquals(EntityEventQueue.CASCADE_LIMIT, receivedCount[0],
            "Event dispatch must halt at cascade limit");
        assertEquals(0, queue.size(), "Queue must be cleared after cascade limit is reached");
    }
}
