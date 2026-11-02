package com.makeasaying.data.save;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

class SaveWriterTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FlagRegistry registry;

    @BeforeEach
    void setUp() {
        FlagDefinition fStage = new FlagDefinition(0, "test.stage", FlagType.INT, FlagScope.GLOBAL, 0, 0, 10, null, "doc");
        FlagDefinition fDoor = new FlagDefinition(1, "test.door_open", FlagType.BOOL, FlagScope.GLOBAL, 0, 0, 1, null, "doc");
        FlagDefinition fLever = new FlagDefinition(2, "test.lever_state", FlagType.ENUM, FlagScope.GLOBAL, 0, 0, 2, List.of("off", "half", "on"), "doc");
        registry = new FlagRegistry(List.of(fStage, fDoor, fLever));
    }

    @Test
    void testTypedFlagValuesInFile() throws Exception {
        SavePayload payload = new SavePayload(
            120L, "2026-11-02T08:00:00Z", "test_01", "savepoint_1",
            Map.of("test.stage", 5, "test.door_open", true, "test.lever_state", 1), // 1 = "half"
            Collections.emptyMap(), Collections.emptyMap()
        );

        String json = SaveWriter.serialize(payload, registry);
        ObjectNode root = (ObjectNode) MAPPER.readTree(json);
        ObjectNode data = (ObjectNode) root.get("data");
        ObjectNode flags = (ObjectNode) data.get("globalFlags");

        // Verify typed representation in JSON
        assertTrue(flags.get("test.door_open").isBoolean(), "Boolean flag must be serialized as JSON boolean");
        assertTrue(flags.get("test.door_open").asBoolean());
        assertTrue(flags.get("test.stage").isInt(), "Int flag must be serialized as JSON number");
        assertEquals(5, flags.get("test.stage").asInt());
        assertTrue(flags.get("test.lever_state").isTextual(), "Enum flag must be serialized by name, never index");
        assertEquals("half", flags.get("test.lever_state").asText());
    }

    @Test
    void testReorderedEnumValuesListDoesNotChangeLoadedValue() {
        // Authored save with lever_state = "half"
        SavePayload payload = new SavePayload(
            120L, "2026-11-02T08:00:00Z", "test_01", "savepoint_1",
            Map.of("test.lever_state", "half"),
            Collections.emptyMap(), Collections.emptyMap()
        );
        String json = SaveWriter.serialize(payload, registry);

        // Registry A: ["off", "half", "on"] -> "half" is index 1
        SaveEnvelope envA = SaveLoader.load(json, registry);
        assertEquals(1, envA.data().globalFlags().get("test.lever_state"));

        // Registry B: reordered enum values ["on", "half", "off"] -> "half" is index 1
        // Registry C: reordered enum values ["half", "off", "on"] -> "half" is index 0
        FlagDefinition fLeverReordered = new FlagDefinition(0, "test.lever_state", FlagType.ENUM, FlagScope.GLOBAL, 0, 0, 2, List.of("half", "off", "on"), "doc");
        FlagRegistry registryReordered = new FlagRegistry(List.of(fLeverReordered));

        SaveEnvelope envC = SaveLoader.load(json, registryReordered);
        assertEquals(0, envC.data().globalFlags().get("test.lever_state"),
            "Loading enum flag by name must resolve to index in the active registry, regardless of enum order");
    }

    @Test
    void testReservedSectionsRoundTripByteEquivalently() throws Exception {
        // Fabricate an unknown future "inventory" section with nested objects and arrays
        String inventoryJson = """
        {
          "slots": [
            { "id": "potion", "count": 3, "slotIndex": 0 },
            { "id": "key_brass", "count": 1, "slotIndex": 4 }
          ],
          "capacity": 20
        }
        """;
        var invNode = MAPPER.readTree(inventoryJson);

        SavePayload payload = new SavePayload(
            250L, "2026-11-02T08:15:00Z", "test_01", "savepoint_1",
            Map.of("test.stage", 1, "test.door_open", false, "test.lever_state", "off"),
            Collections.emptyMap(),
            Map.of("inventory", invNode)
        );

        String writtenJson = SaveWriter.serialize(payload, registry);

        // Load back
        SaveEnvelope loaded = SaveLoader.load(writtenJson, registry);
        assertTrue(loaded.data().reservedSections().containsKey("inventory"));

        // Serialize loaded envelope back
        String rewrittenJson = SaveWriter.serialize(loaded.data(), registry);

        assertEquals(writtenJson, rewrittenJson,
            "Save with fabricated reserved section must round-trip byte-identically without data loss");
    }
}
