package com.makeasaying.data.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomEntityData;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomMigrationV3Test {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testV1ToV3MigrationAddsExitsAndEntitySeq() throws IOException {
        Path path = Path.of("src/test/resources/fixtures/test_room_v1.json");
        if (!Files.exists(path)) {
            path = Path.of("data/src/test/resources/fixtures/test_room_v1.json");
        }
        assertTrue(Files.exists(path), "test_room_v1.json fixture must exist");

        Room room = RoomLoader.load(path);
        assertEquals("fixture_v1", room.id());
        assertEquals(1, room.entitySeq(), "Empty entity list should default entitySeq to 1");
        assertNotNull(room.exits());
        assertTrue(room.exits().isEmpty());
    }

    @Test
    void testV2ToV3MigrationComputesEntitySeqAndConvertsOverrides() throws IOException {
        Path path = Path.of("src/test/resources/fixtures/test_room_v2.json");
        if (!Files.exists(path)) {
            path = Path.of("data/src/test/resources/fixtures/test_room_v2.json");
        }
        assertTrue(Files.exists(path), "test_room_v2.json fixture must exist");

        Room room = RoomLoader.load(path);
        assertEquals("fixture_v2", room.id());
        // max numeric suffix was 9 (from moving_platform_9), so entitySeq should be 10
        assertEquals(10, room.entitySeq(), "entitySeq must be max(suffixes) + 1");
        assertEquals(2, room.entities().size());

        RoomEntityData entity1 = room.entities().get(0);
        assertEquals("moving_platform_4", entity1.instanceId());
        // Flat overrides speed & travelX must be converted to nested under "platform"
        assertTrue(entity1.overrides().containsKey("platform"), "Flat overrides must migrate to component-nested");
        Map<String, Object> platOverrides = entity1.overrides().get("platform");
        assertEquals(60.0, ((Number) platOverrides.get("speed")).doubleValue(), 0.001);
        assertEquals(32, ((Number) platOverrides.get("travelX")).intValue());

        RoomEntityData entity2 = room.entities().get(1);
        assertEquals("moving_platform_9", entity2.instanceId());
        assertTrue(entity2.overrides().isEmpty());
    }

    @Test
    void testRejectsNewerVersionV4() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", 4);
        root.put("id", "future_room");
        root.put("width", 20);
        root.put("height", 10);

        assertThrows(IllegalArgumentException.class, () -> {
            RoomLoader.MIGRATION_CHAIN.migrate(root);
            RoomLoader.fromData(MAPPER.treeToValue(root, RoomLoader.RoomData.class));
        });
    }
}
