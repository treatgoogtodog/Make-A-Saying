package com.makeasaying.data.world;

import com.makeasaying.data.condition.ConditionLoader;
import com.makeasaying.engine.condition.AtomRegistry;
import com.makeasaying.engine.condition.CompiledCondition;
import com.makeasaying.engine.entity.EntityDefinition;
import com.makeasaying.engine.entity.EntitySpawner;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.flags.FlagChangeLogEntry;
import com.makeasaying.engine.flags.FlagDefinition;
import com.makeasaying.engine.flags.FlagRegistry;
import com.makeasaying.engine.flags.FlagScope;
import com.makeasaying.engine.flags.FlagStore;
import com.makeasaying.engine.flags.FlagType;
import com.makeasaying.engine.schema.ComponentSchemaRegistry;
import com.makeasaying.engine.world.RoomEntityData;
import com.makeasaying.engine.world.RoomExit;
import com.makeasaying.engine.world.RoomStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnAndExitConditionTest {
    private FlagRegistry flagRegistry;
    private FlagStore flagStore;
    private RoomStateStore roomStateStore;
    private EntitySpawner spawner;
    private EntityDefinition genericDef;

    @BeforeEach
    void setUp() {
        FlagDefinition defStage = new FlagDefinition(0, "test.stage", FlagType.INT, FlagScope.GLOBAL, 0, 0, 10, null, "doc");
        FlagDefinition defDoor = new FlagDefinition(1, "test.door_open", FlagType.BOOL, FlagScope.ROOM, 0, 0, 1, null, "doc");
        flagRegistry = new FlagRegistry(List.of(defStage, defDoor));
        roomStateStore = new RoomStateStore();
        flagStore = new FlagStore(flagRegistry, roomStateStore);

        ComponentSchemaRegistry schemaRegistry = ComponentSchemaRegistry.createDefault();
        spawner = new EntitySpawner(schemaRegistry);

        genericDef = new EntityDefinition(
            1, "generic", List.of(), "#FFFFFF", true,
            Map.of("body", Map.of("x", 0, "y", 0), "collider", Map.of("width", 16, "height", 16))
        );
    }

    @Test
    void testSpawnConditionFalseSkipsSpawnWhilePreservingIdSequence() {
        World world = new World();

        // Node array for condition: flag test.door_open (index 1) == 1
        CompiledCondition condDoorOpen = new CompiledCondition(new CompiledCondition.Node[]{
            new CompiledCondition.Node(CompiledCondition.OP_FLAG_EQ, 1, 1, new int[0])
        });

        RoomEntityData ent1 = new RoomEntityData("ent_1", "generic", 32, 16, Map.of(), CompiledCondition.ALWAYS_TRUE, null);
        RoomEntityData ent2 = new RoomEntityData("ent_2", "generic", 64, 16, Map.of(), condDoorOpen, null);
        RoomEntityData ent3 = new RoomEntityData("ent_3", "generic", 96, 16, Map.of(), CompiledCondition.ALWAYS_TRUE, null);

        List<RoomEntityData> entities = List.of(ent1, ent2, ent3);

        // 1. Initial state: test.door_open is false (0)
        assertEquals(0, flagStore.get(1, "test_01"));

        for (RoomEntityData ed : entities) {
            if (ed.spawnCondition() == null || ed.spawnCondition().evaluate(flagStore, "test_01")) {
                spawner.spawn(world, genericDef, ed.instanceId(), ed.x(), ed.y(), ed.overrides());
            }
        }
        world.applyDeferredLifecycle();

        // ent_1 and ent_3 spawned, ent_2 skipped
        assertEquals(2, world.getActiveCount());
        assertNotNull(world.findEntityByInstanceId("ent_1"));
        assertNull(world.findEntityByInstanceId("ent_2"), "ent_2 must not be spawned when condition evaluates to false");
        assertNotNull(world.findEntityByInstanceId("ent_3"));

        // ent_2 ID was preserved in room definition; now set test.door_open = 1 and respawn
        World world2 = new World();
        flagStore.set(1, 1, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        flagStore.applyPendingChanges("test_01", null, 1L);
        assertEquals(1, flagStore.get(1, "test_01"));

        for (RoomEntityData ed : entities) {
            if (ed.spawnCondition() == null || ed.spawnCondition().evaluate(flagStore, "test_01")) {
                spawner.spawn(world2, genericDef, ed.instanceId(), ed.x(), ed.y(), ed.overrides());
            }
        }
        world2.applyDeferredLifecycle();

        // Now all 3 entities are spawned cleanly
        assertEquals(3, world2.getActiveCount());
        assertNotNull(world2.findEntityByInstanceId("ent_1"));
        assertNotNull(world2.findEntityByInstanceId("ent_2"), "ent_2 must spawn when condition is true");
        assertNotNull(world2.findEntityByInstanceId("ent_3"));
    }

    @Test
    void testExitConditionBlocksAndEnablesExit() {
        // Exit condition: flag test.stage (index 0) >= 1
        CompiledCondition condStage1 = new CompiledCondition(new CompiledCondition.Node[]{
            new CompiledCondition.Node(CompiledCondition.OP_FLAG_GE, 0, 1, new int[0])
        });

        RoomExit exit = new RoomExit("to_test_02", 200, 16, 24, 48, "test_02", "spawn_01", condStage1, null);

        // Stage initially 0 -> exit condition fails
        assertEquals(0, flagStore.get(0, "test_01"));
        assertFalse(exit.condition().evaluate(flagStore, "test_01"), "Exit condition must evaluate to false when stage < 1");

        // Mutate stage to 1 -> exit condition passes
        flagStore.set(0, 1, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        flagStore.applyPendingChanges("test_01", null, 5L);
        assertEquals(1, flagStore.get(0, "test_01"));
        assertTrue(exit.condition().evaluate(flagStore, "test_01"), "Exit condition must evaluate to true when stage >= 1");
    }
}
