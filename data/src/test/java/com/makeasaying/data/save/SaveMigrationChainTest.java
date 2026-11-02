package com.makeasaying.data.save;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SaveMigrationChainTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testSyntheticV1ToV2Migration() throws Exception {
        SaveMigrationChain chain = new SaveMigrationChain();

        // Migration step v1 -> v2: renames "playtimeTicks" to "playtimeSimulationTicks"
        chain.register(1, root -> {
            ObjectNode data = (ObjectNode) root.get("data");
            long pt = data.get("playtimeTicks").asLong();
            data.remove("playtimeTicks");
            data.put("playtimeSimulationTicks", pt);
        });

        String v1Json = """
        {
          "version": 1,
          "gameVersion": "0.1.0",
          "checksum": "test",
          "data": {
            "playtimeTicks": 500,
            "currentRoomId": "test_01",
            "savepointInstanceId": "savepoint_1"
          }
        }
        """;
        ObjectNode root = (ObjectNode) MAPPER.readTree(v1Json);

        int migratedVersion = chain.migrate(root, 2);
        assertEquals(2, migratedVersion);
        assertEquals(2, root.get("version").asInt());

        ObjectNode data = (ObjectNode) root.get("data");
        assertFalse(data.has("playtimeTicks"));
        assertTrue(data.has("playtimeSimulationTicks"));
        assertEquals(500L, data.get("playtimeSimulationTicks").asLong());
    }

    @Test
    void testRejectUnknownNewerVersion() throws Exception {
        SaveMigrationChain chain = new SaveMigrationChain();
        String futureJson = "{\"version\": 99, \"data\": {}}";
        ObjectNode root = (ObjectNode) MAPPER.readTree(futureJson);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> chain.migrate(root, 1));
        assertTrue(ex.getMessage().contains("SAVE_ERR_UNKNOWN_NEWER_VERSION"));
    }
}
