package com.makeasaying.engine.world;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoomStateStoreTest {
    private RoomStateStore store;

    @BeforeEach
    void setUp() {
        store = new RoomStateStore();
    }

    @Test
    void testBasicIntAndStringOperations() {
        store.setInt("room_01", "visited", 1);
        store.setString("room_01", "status", "cleared");

        assertEquals(1, store.getInt("room_01", "visited", 0));
        assertEquals("cleared", store.getString("room_01", "status", "unknown"));
        assertTrue(store.has("room_01", "visited"));
        assertTrue(store.has("room_01", "status"));
        assertFalse(store.has("room_01", "nonexistent"));
        assertEquals(99, store.getInt("room_01", "missing", 99));
        assertEquals("default", store.getString("room_01", "missing", "default"));
    }

    @Test
    void testIncrementInt() {
        assertEquals(1, store.incrementInt("room_a", "visited"));
        assertEquals(2, store.incrementInt("room_a", "visited"));
        assertEquals(3, store.incrementInt("room_a", "visited"));
        assertEquals(3, store.getInt("room_a", "visited", 0));
    }

    @Test
    void testDeterministicStateHash() {
        store.setInt("room_b", "kills", 5);
        store.setInt("room_a", "visited", 1);
        long hash1 = store.stateHash();

        RoomStateStore store2 = new RoomStateStore();
        store2.setInt("room_a", "visited", 1);
        store2.setInt("room_b", "kills", 5);
        long hash2 = store2.stateHash();

        assertEquals(hash1, hash2, "Insertion order must not affect deterministic stateHash");

        store2.incrementInt("room_a", "visited");
        assertNotEquals(hash1, store2.stateHash(), "State changes must alter stateHash");
    }

    @Test
    void testInvalidKeyValidation() {
        assertThrows(IllegalArgumentException.class, () -> store.setInt("room_01", "Invalid-Key", 1));
        assertThrows(IllegalArgumentException.class, () -> store.setInt("room_01", "", 1));
        assertThrows(IllegalArgumentException.class, () -> store.setInt("", "valid_key", 1));
    }
}
