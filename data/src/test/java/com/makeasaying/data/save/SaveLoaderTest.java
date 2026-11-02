package com.makeasaying.data.save;

import com.makeasaying.engine.flags.FlagDefinition;
import com.makeasaying.engine.flags.FlagRegistry;
import com.makeasaying.engine.flags.FlagScope;
import com.makeasaying.engine.flags.FlagType;
import com.makeasaying.engine.save.SaveEnvelope;
import com.makeasaying.engine.save.SavePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SaveLoaderTest {
    private FlagRegistry registry;

    @BeforeEach
    void setUp() {
        FlagDefinition fStage = new FlagDefinition(0, "test.stage", FlagType.INT, FlagScope.GLOBAL, 0, 0, 10, null, "doc");
        FlagDefinition fDoor = new FlagDefinition(1, "test.door_open", FlagType.BOOL, FlagScope.ROOM, 0, 0, 1, null, "doc");
        registry = new FlagRegistry(List.of(fStage, fDoor));
    }

    @Test
    void testTamperRejectedByChecksum() {
        SavePayload payload = new SavePayload(
            100L, "2026-11-02T08:00:00Z", "test_01", "savepoint_1",
            Map.of("test.stage", 1), Collections.emptyMap(), Collections.emptyMap()
        );
        String json = SaveWriter.serialize(payload, registry);

        // Tamper with data: replace playtimeTicks 100 with 999
        String tampered = json.replace("\"playtimeTicks\" : 100", "\"playtimeTicks\" : 999");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> SaveLoader.load(tampered, registry));
        assertTrue(ex.getMessage().contains("SAVE_ERR_CHECKSUM_MISMATCH"),
            "Expected checksum mismatch error, got: " + ex.getMessage());
    }

    @Test
    void testUnknownGlobalFlagDroppedWithWarning() {
        // Save file with an undeclared flag "legacy.key"
        String rawData = """
        {
          "version": 1,
          "gameVersion": "0.1.0",
          "data": {
            "playtimeTicks": 50,
            "timestampUtc": "2026-11-02T08:00:00Z",
            "currentRoomId": "test_01",
            "savepointInstanceId": "savepoint_1",
            "globalFlags": {
              "test.stage": 4,
              "legacy.key": 99
            },
            "roomRecords": {}
          }
        }
        """;
        String dataOnly = "{\"currentRoomId\":\"test_01\",\"globalFlags\":{\"legacy.key\":99,\"test.stage\":4},\"playtimeTicks\":50,\"roomRecords\":{},\"savepointInstanceId\":\"savepoint_1\",\"timestampUtc\":\"2026-11-02T08:00:00Z\"}";
        String checksum = SaveWriter.computeSha256(dataOnly);
        String json = rawData.replace("\"data\": {", "\"checksum\": \"" + checksum + "\",\n  \"data\": {");

        SaveEnvelope env = SaveLoader.load(json, registry);
        assertNotNull(env);
        // legacy.key must be dropped
        assertFalse(env.data().globalFlags().containsKey("legacy.key"), "Unknown flag must be dropped");
        assertEquals(4, env.data().globalFlags().get("test.stage"));
    }

    @Test
    void testOutOfRangeGlobalFlagResetToDefault() {
        // test.stage range is [0..10], author 99
        SavePayload payload = new SavePayload(
            100L, "2026-11-02T08:00:00Z", "test_01", "savepoint_1",
            Map.of("test.stage", 99), Collections.emptyMap(), Collections.emptyMap()
        );
        String json = SaveWriter.serialize(payload, registry);

        SaveEnvelope env = SaveLoader.load(json, registry);
        // Reset to default (0)
        assertEquals(0, env.data().globalFlags().get("test.stage"),
            "Out of range flag value must be reset to registry default");
    }

    @Test
    void testRoomRecordFlagValidationUnknownAndOutOfRange() {
        // Room records with unknown flag and out-of-range room flag
        Map<String, Map<String, Object>> roomRecords = Map.of(
            "test_01", Map.of(
                "flag.unknown_flag", Map.of("type", "INT", "value", 1),
                "flag.test.door_open", Map.of("type", "INT", "value", 42) // bool door_open only accepts 0 or 1
            )
        );
        SavePayload payload = new SavePayload(
            100L, "2026-11-02T08:00:00Z", "test_01", "savepoint_1",
            Collections.emptyMap(), roomRecords, Collections.emptyMap()
        );
        String json = SaveWriter.serialize(payload, registry);

        SaveEnvelope env = SaveLoader.load(json, registry);
        Map<String, Object> room01 = env.data().roomRecords().get("test_01");
        assertNotNull(room01);

        // unknown_flag dropped
        assertFalse(room01.containsKey("flag.unknown_flag"), "Unknown room flag must be dropped");

        // door_open reset to default (0)
        Map<String, Object> doorEntry = (Map<String, Object>) room01.get("flag.test.door_open");
        assertNotNull(doorEntry);
        assertEquals(0, ((Number) doorEntry.get("value")).intValue(),
            "Out of range room flag must be reset to default");
    }

    @Test
    void testRejectUnknownNewerVersion() {
        String json = """
        {
          "version": 2,
          "gameVersion": "0.2.0",
          "checksum": "abc",
          "data": {
            "currentRoomId": "test_01",
            "savepointInstanceId": "savepoint_1"
          }
        }
        """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> SaveLoader.load(json, registry));
        assertTrue(ex.getMessage().contains("SAVE_ERR_UNKNOWN_NEWER_VERSION"));
    }
}
