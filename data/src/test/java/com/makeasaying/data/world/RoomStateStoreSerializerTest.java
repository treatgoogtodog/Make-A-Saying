package com.makeasaying.data.world;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.makeasaying.engine.world.RoomStateStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoomStateStoreSerializerTest {
    @Test
    void testSerializationRoundTrip() {
        RoomStateStore store = new RoomStateStore();
        store.setInt("test_01", "visited", 3);
        store.setString("test_01", "last_event", "boss_dead");
        store.setInt("test_02", "visited", 1);

        ObjectNode tree = RoomStateStoreSerializer.toTree(store);
        assertNotNull(tree);
        assertTrue(tree.has("test_01"));
        assertTrue(tree.has("test_02"));
        assertEquals(3, tree.get("test_01").get("visited").asInt());
        assertEquals("boss_dead", tree.get("test_01").get("last_event").asText());

        RoomStateStore loaded = new RoomStateStore();
        RoomStateStoreSerializer.fromTree(tree, loaded);

        assertEquals(store.stateHash(), loaded.stateHash());
        assertEquals(3, loaded.getInt("test_01", "visited", 0));
        assertEquals("boss_dead", loaded.getString("test_01", "last_event", ""));
        assertEquals(1, loaded.getInt("test_02", "visited", 0));
    }
}
