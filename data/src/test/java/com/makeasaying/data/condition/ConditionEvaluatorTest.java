package com.makeasaying.data.condition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeasaying.engine.condition.AtomRegistry;
import com.makeasaying.engine.condition.CompiledCondition;
import com.makeasaying.engine.flags.FlagChangeLogEntry;
import com.makeasaying.engine.flags.FlagDefinition;
import com.makeasaying.engine.flags.FlagRegistry;
import com.makeasaying.engine.flags.FlagScope;
import com.makeasaying.engine.flags.FlagStore;
import com.makeasaying.engine.flags.FlagType;
import com.makeasaying.engine.world.RoomStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionEvaluatorTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FlagRegistry registry;
    private AtomRegistry atomRegistry;
    private FlagStore store;

    @BeforeEach
    void setup() {
        FlagDefinition fStage = new FlagDefinition(0, "test.stage", FlagType.INT, FlagScope.GLOBAL, 0, 0, 10, null, "doc");
        FlagDefinition fDoor = new FlagDefinition(1, "test.door_open", FlagType.BOOL, FlagScope.ROOM, 0, 0, 1, null, "doc");
        FlagDefinition fLever = new FlagDefinition(2, "test.lever_state", FlagType.ENUM, FlagScope.SESSION, 0, 0, 2, List.of("off", "half", "on"), "doc");

        registry = new FlagRegistry(List.of(fStage, fDoor, fLever));
        atomRegistry = new AtomRegistry();
        store = new FlagStore(registry, new RoomStateStore());
    }

    @Test
    void testEmptyAndNullConditionsEvaluateTrue() {
        assertTrue(ConditionLoader.parse(null, registry, atomRegistry).evaluate(store, "room_01"));
        assertTrue(ConditionLoader.parse(MAPPER.createObjectNode(), registry, atomRegistry).evaluate(store, "room_01"));
    }

    @Test
    void testAllComparisonOperators() throws Exception {
        // Stage starts at 0
        CompiledCondition eq0 = ConditionLoader.parse(MAPPER.readTree("{\"flag\": \"test.stage\", \"op\": \"==\", \"value\": 0}"), registry, atomRegistry);
        CompiledCondition ne0 = ConditionLoader.parse(MAPPER.readTree("{\"flag\": \"test.stage\", \"op\": \"!=\", \"value\": 0}"), registry, atomRegistry);
        CompiledCondition gt0 = ConditionLoader.parse(MAPPER.readTree("{\"flag\": \"test.stage\", \"op\": \">\", \"value\": 0}"), registry, atomRegistry);
        CompiledCondition gte0 = ConditionLoader.parse(MAPPER.readTree("{\"flag\": \"test.stage\", \"op\": \">=\", \"value\": 0}"), registry, atomRegistry);
        CompiledCondition lt1 = ConditionLoader.parse(MAPPER.readTree("{\"flag\": \"test.stage\", \"op\": \"<\", \"value\": 1}"), registry, atomRegistry);
        CompiledCondition lte0 = ConditionLoader.parse(MAPPER.readTree("{\"flag\": \"test.stage\", \"op\": \"<=\", \"value\": 0}"), registry, atomRegistry);

        assertTrue(eq0.evaluate(store, "room_01"));
        assertFalse(ne0.evaluate(store, "room_01"));
        assertFalse(gt0.evaluate(store, "room_01"));
        assertTrue(gte0.evaluate(store, "room_01"));
        assertTrue(lt1.evaluate(store, "room_01"));
        assertTrue(lte0.evaluate(store, "room_01"));

        // Mutate stage to 5
        store.set(0, 5, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.applyPendingChanges("room_01", null, 1L);

        assertFalse(eq0.evaluate(store, "room_01"));
        assertTrue(ne0.evaluate(store, "room_01"));
        assertTrue(gt0.evaluate(store, "room_01"));
        assertTrue(gte0.evaluate(store, "room_01"));
        assertFalse(lt1.evaluate(store, "room_01"));
        assertFalse(lte0.evaluate(store, "room_01"));
    }

    @Test
    void testCombinatorsAllAnyNot() throws Exception {
        String json = """
        {
          "all": [
            { "flag": "test.stage", "op": ">=", "value": 1 },
            {
              "any": [
                { "flag": "test.door_open", "op": "==", "value": true },
                { "not": { "flag": "test.lever_state", "op": "==", "value": "off" } }
              ]
            }
          ]
        }
        """;
        CompiledCondition cond = ConditionLoader.parse(MAPPER.readTree(json), registry, atomRegistry);
        assertNotNull(cond);

        // Initially: stage=0, door=false, lever=off -> all fails because stage < 1
        assertFalse(cond.evaluate(store, "room_01"));

        // Set stage=2: still false because door=false and lever=off (so any branch is false)
        store.set(0, 2, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.applyPendingChanges("room_01", null, 2L);
        assertFalse(cond.evaluate(store, "room_01"));

        // Turn lever to "half": now not(lever == off) is true, so any is true, so all is true!
        store.set(2, 1, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.applyPendingChanges("room_01", null, 3L);
        assertTrue(cond.evaluate(store, "room_01"));
    }

    @Test
    void testFlagSetAtom() throws Exception {
        // Flag set atom checks that boolean flag is true (1)
        CompiledCondition cond = ConditionLoader.parse(MAPPER.readTree("{\"flag_set\": \"test.door_open\"}"), registry, atomRegistry);

        assertFalse(cond.evaluate(store, "room_01"));

        store.set(1, 1, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        store.applyPendingChanges("room_01", null, 4L);

        assertTrue(cond.evaluate(store, "room_01"));
    }

    @Test
    void testRejectUnknownAtom() {
        String json = "{\"has_magic_wand\": \"wand_01\"}";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ConditionLoader.parse(MAPPER.readTree(json), registry, atomRegistry));
        assertTrue(ex.getMessage().contains("COND_ERR_UNKNOWN_ATOM"));
    }

    @Test
    void testRejectUnknownFlag() {
        String json = "{\"flag\": \"unknown.flag.id\", \"op\": \"==\", \"value\": 1}";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ConditionLoader.parse(MAPPER.readTree(json), registry, atomRegistry));
        assertTrue(ex.getMessage().contains("COND_ERR_UNKNOWN_FLAG"));
    }

    @Test
    void testRejectDepthExceeded() {
        // Build 9 nested "not" nodes (depth exceeds MAX_DEPTH=8)
        String json = "{\"not\":{\"not\":{\"not\":{\"not\":{\"not\":{\"not\":{\"not\":{\"not\":{\"not\":{\"flag\":\"test.stage\",\"op\":\"==\",\"value\":0}}}}}}}}}}";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ConditionLoader.parse(MAPPER.readTree(json), registry, atomRegistry));
        assertTrue(ex.getMessage().contains("COND_ERR_DEPTH_EXCEEDED"));
    }

    @Test
    void testRejectFlagSetOnEnum() {
        // flag_set on enum flag (test.lever_state) must be a validation error
        String json = "{\"flag_set\": \"test.lever_state\"}";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ConditionLoader.parse(MAPPER.readTree(json), registry, atomRegistry));
        assertTrue(ex.getMessage().contains("COND_ERR_FLAG_SET_ON_ENUM"),
            "Expected COND_ERR_FLAG_SET_ON_ENUM error, got: " + ex.getMessage());
    }
}
