package com.makeasaying.data.save;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeasaying.data.condition.ConditionLoader;
import com.makeasaying.data.entity.EntityDefinitionLoader;
import com.makeasaying.data.world.RoomLoader;
import com.makeasaying.engine.async.Jobs;
import com.makeasaying.engine.condition.AtomRegistry;
import com.makeasaying.engine.entity.ControlSource;
import com.makeasaying.engine.entity.EntityDefinition;
import com.makeasaying.engine.entity.EntitySpawner;
import com.makeasaying.engine.entity.MovementTuning;
import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.engine.entity.TileShape;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.ControllerComponent;
import com.makeasaying.engine.entity.components.InputStateComponent;
import com.makeasaying.engine.entity.components.JumpStateComponent;
import com.makeasaying.engine.entity.components.PlatformComponent;
import com.makeasaying.engine.entity.components.RoomScopeComponent;
import com.makeasaying.engine.entity.components.SavepointComponent;
import com.makeasaying.engine.entity.events.EntityEventQueue;
import com.makeasaying.engine.entity.systems.MovementSystem;
import com.makeasaying.engine.entity.systems.PlatformSystem;
import com.makeasaying.engine.entity.systems.PlayerControlSystem;
import com.makeasaying.engine.entity.systems.SavepointSystem;
import com.makeasaying.engine.flags.FlagDefinition;
import com.makeasaying.engine.flags.FlagRegistry;
import com.makeasaying.engine.flags.FlagScope;
import com.makeasaying.engine.flags.FlagStore;
import com.makeasaying.engine.flags.FlagType;
import com.makeasaying.engine.input.ActionDefinition;
import com.makeasaying.engine.input.ActionRegistry;
import com.makeasaying.engine.input.Actions;
import com.makeasaying.engine.input.InputState;
import com.makeasaying.engine.save.SaveEnvelope;
import com.makeasaying.engine.save.SavePayload;
import com.makeasaying.engine.schema.ComponentSchemaRegistry;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomEntityData;
import com.makeasaying.engine.world.RoomStateStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class CheckpointDeterminismTest {

    private Jobs jobs;
    private FlagRegistry registry;
    private ActionRegistry actionRegistry;

    private static Path resolveContentPath(String relative) {
        Path p = Path.of(relative);
        if (!Files.exists(p)) {
            p = Path.of("..", relative);
        }
        return p;
    }

    @BeforeEach
    void setUp() {
        jobs = new Jobs(2);
        FlagDefinition fStage = new FlagDefinition(0, "test.stage", FlagType.INT, FlagScope.GLOBAL, 0, 0, 100, null, "doc");
        FlagDefinition fDoor = new FlagDefinition(1, "test.door_open", FlagType.BOOL, FlagScope.GLOBAL, 0, 0, 1, null, "doc");
        FlagDefinition fLever = new FlagDefinition(2, "test.lever_state", FlagType.ENUM, FlagScope.GLOBAL, 0, 0, 2, List.of("off", "on", "broken"), "doc");
        FlagDefinition fSession = new FlagDefinition(3, "session.unlocked", FlagType.BOOL, FlagScope.SESSION, 0, 0, 1, null, "doc");
        FlagDefinition fRoomTemp = new FlagDefinition(4, "temp.active", FlagType.BOOL, FlagScope.ROOM_TEMP, 0, 0, 1, null, "doc");
        FlagDefinition fChest = new FlagDefinition(5, "chest_opened", FlagType.INT, FlagScope.ROOM, 0, 0, 10, null, "doc");
        registry = new FlagRegistry(List.of(fStage, fDoor, fLever, fSession, fRoomTemp, fChest));

        actionRegistry = new ActionRegistry();
        actionRegistry.register(ActionDefinition.button(Actions.MOVE_LEFT));
        actionRegistry.register(ActionDefinition.button(Actions.MOVE_RIGHT));
        actionRegistry.register(ActionDefinition.button(Actions.MOVE_DOWN));
        actionRegistry.register(ActionDefinition.button(Actions.MOVE_UP));
        actionRegistry.register(ActionDefinition.button(Actions.JUMP));
        actionRegistry.register(ActionDefinition.button(Actions.INTERACT));
        actionRegistry.register(ActionDefinition.axis(Actions.MOVE_X, Actions.MOVE_LEFT, Actions.MOVE_RIGHT));
        actionRegistry.register(ActionDefinition.axis(Actions.MOVE_Y, Actions.MOVE_DOWN, Actions.MOVE_UP));
    }

    @AfterEach
    void tearDown() {
        jobs.shutdown();
    }

    @Test
    void testReplayFastVsSlowSaveLocationHashParity() {
        // Run A: fast memory location (0ms)
        MemorySaveLocation fastLoc = new MemorySaveLocation();
        SaveManager fastManager = new SaveManager(fastLoc, registry, jobs, () -> "2026-11-02T09:00:00Z");

        // Run B: slow memory location (simulated delay, second save requested while first is in flight)
        MemorySaveLocation slowLoc = new MemorySaveLocation();
        slowLoc.setWriteDelayMs(10);
        SaveManager slowManager = new SaveManager(slowLoc, registry, jobs, () -> "2026-11-02T09:00:00Z");

        World worldA = createSimWorld();
        World worldB = createSimWorld();

        RoomStateStore rssA = new RoomStateStore();
        RoomStateStore rssB = new RoomStateStore();

        FlagStore fsA = new FlagStore(registry, rssA);
        FlagStore fsB = new FlagStore(registry, rssB);

        SavepointSystem spsA = new SavepointSystem();
        SavepointSystem spsB = new SavepointSystem();

        AtomicInteger saveCompletionsA = new AtomicInteger();
        AtomicInteger saveCompletionsB = new AtomicInteger();

        // Simulate 200 ticks
        for (int tick = 0; tick < 200; tick++) {
            // Drain worker jobs on main thread
            jobs.drain();

            // At tick 20: trigger save on both
            if (tick == 20) {
                SavePayload payloadA = new SavePayload(tick, "2026-11-02T09:00:00Z", "test_01", "savepoint_1",
                    fsA.exportGlobalFlags(), rssA.exportRoomRecords(), Collections.emptyMap());
                fastManager.requestSave("slot_1", payloadA, success -> saveCompletionsA.incrementAndGet());

                SavePayload payloadB = new SavePayload(tick, "2026-11-02T09:00:00Z", "test_01", "savepoint_1",
                    fsB.exportGlobalFlags(), rssB.exportRoomRecords(), Collections.emptyMap());
                slowManager.requestSave("slot_1", payloadB, success -> saveCompletionsB.incrementAndGet());
            }

            // At tick 22: trigger another save while slowLoc might be processing
            if (tick == 22) {
                SavePayload payloadA = new SavePayload(tick, "2026-11-02T09:00:01Z", "test_01", "savepoint_1",
                    fsA.exportGlobalFlags(), rssA.exportRoomRecords(), Collections.emptyMap());
                fastManager.requestSave("slot_1", payloadA, success -> saveCompletionsA.incrementAndGet());

                SavePayload payloadB = new SavePayload(tick, "2026-11-02T09:00:01Z", "test_01", "savepoint_1",
                    fsB.exportGlobalFlags(), rssB.exportRoomRecords(), Collections.emptyMap());
                slowManager.requestSave("slot_1", payloadB, success -> saveCompletionsB.incrementAndGet());
            }

            // Run simulation tick on both
            stepSimulation(worldA, fsA, rssA, spsA, tick);
            stepSimulation(worldB, fsB, rssB, spsB, tick);

            // Assert exact state hash parity on every tick
            assertEquals(worldA.stateHash(), worldB.stateHash(),
                "World state hash mismatch between fast and slow storage at tick " + tick);
            assertEquals(fsA.computeStateHash("test_01"), fsB.computeStateHash("test_01"),
                "FlagStore state hash mismatch between fast and slow storage at tick " + tick);
            assertEquals(rssA.stateHash(), rssB.stateHash(),
                "RoomStateStore state hash mismatch between fast and slow storage at tick " + tick);
        }
    }

    @Test
    void testPreFadeValidationRejectsCorruptSaveMissingRoomAndDeletedSavepoint() throws Exception {
        MemorySaveLocation loc = new MemorySaveLocation();
        SaveManager manager = new SaveManager(loc, registry, jobs, () -> "2026-11-02T09:00:00Z");

        com.makeasaying.data.world.RoomCache roomCache = new com.makeasaying.data.world.RoomCache();
        Path authoredRoomPath = resolveContentPath("content/rooms/test_01.json");
        Room room1 = RoomLoader.load(authoredRoomPath);
        roomCache.put(room1, authoredRoomPath);

        // 1. Corrupt save (bad checksum)
        String corruptJson = """
            {
              "version": 1,
              "gameVersion": "0.1.0",
              "checksum": "bad_checksum_hash_12345",
              "data": {
                "playtimeTicks": 10,
                "timestampUtc": "2026-11-02T09:00:00Z",
                "currentRoomId": "test_01",
                "savepointInstanceId": "savepoint_3",
                "globalFlags": {},
                "roomRecords": {}
              }
            }
            """;
        loc.putRaw("corrupt_slot.json", corruptJson);
        java.util.concurrent.CountDownLatch latch1 = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean failed1 = new java.util.concurrent.atomic.AtomicBoolean(false);
        manager.preValidateAndLoad("corrupt_slot", roomCache, env -> {
            latch1.countDown();
            fail("Should not succeed");
        }, () -> {
            failed1.set(true);
            latch1.countDown();
        });
        drainUntilDone(latch1);
        assertTrue(failed1.get(), "Corrupt save checksum must fail pre-validation before fading");

        // 2. Missing room file
        SavePayload pMissingRoom = new SavePayload(
            100L, "2026-11-02T09:00:00Z", "non_existent_room_99", "savepoint_1",
            Map.of("test.stage", 1), Collections.emptyMap(), Collections.emptyMap()
        );
        String jsonMissingRoom = SaveWriter.serialize(pMissingRoom, registry);
        loc.putRaw("missing_room_slot.json", jsonMissingRoom);
        java.util.concurrent.CountDownLatch latch2 = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean failed2 = new java.util.concurrent.atomic.AtomicBoolean(false);
        manager.preValidateAndLoad("missing_room_slot", roomCache, env -> {
            latch2.countDown();
            fail("Should not succeed");
        }, () -> {
            failed2.set(true);
            latch2.countDown();
        });
        drainUntilDone(latch2);
        assertTrue(failed2.get(), "Save with missing room file must fail pre-validation before fading");

        // 3. Room exists but savepoint entity was deleted / missing
        SavePayload pMissingSp = new SavePayload(
            100L, "2026-11-02T09:00:00Z", "test_01", "deleted_savepoint_instance",
            Map.of("test.stage", 1), Collections.emptyMap(), Collections.emptyMap()
        );
        String jsonMissingSp = SaveWriter.serialize(pMissingSp, registry);
        loc.putRaw("missing_sp_slot.json", jsonMissingSp);
        java.util.concurrent.CountDownLatch latch3 = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean failed3 = new java.util.concurrent.atomic.AtomicBoolean(false);
        manager.preValidateAndLoad("missing_sp_slot", roomCache, env -> {
            latch3.countDown();
            fail("Should not succeed");
        }, () -> {
            failed3.set(true);
            latch3.countDown();
        });
        drainUntilDone(latch3);
        assertTrue(failed3.get(), "Save with missing savepoint in target room must fail pre-validation before fading");

        // 4. Valid save (test_01 with savepoint_3) succeeds
        SavePayload pValid = new SavePayload(
            100L, "2026-11-02T09:00:00Z", "test_01", "savepoint_3",
            Map.of("test.stage", 2), Collections.emptyMap(), Collections.emptyMap()
        );
        String jsonValid = SaveWriter.serialize(pValid, registry);
        loc.putRaw("valid_slot.json", jsonValid);
        java.util.concurrent.CountDownLatch latch4 = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean success4 = new java.util.concurrent.atomic.AtomicBoolean(false);
        manager.preValidateAndLoad("valid_slot", roomCache, env -> {
            success4.set(true);
            assertEquals("test_01", env.data().currentRoomId());
            assertEquals("savepoint_3", env.data().savepointInstanceId());
            latch4.countDown();
        }, () -> {
            latch4.countDown();
            fail("Valid save should not fail");
        });
        drainUntilDone(latch4);
        assertTrue(success4.get(), "Valid save must pass pre-validation");
    }

    private void drainUntilDone(java.util.concurrent.CountDownLatch latch) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 3000) {
            jobs.drain();
            if (latch.await(10, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                jobs.drain();
                break;
            }
        }
    }

    @Test
    void testExactSwapTickStateSequence() throws Exception {
        // Documented 6-step swap tick sequence:
        // 1. replace roomStateStore with save's room records
        // 2. set global flags from save, retaining untouched defaults for missing ones
        // 3. reset session and room_temp flags to defaults
        // 4. load room and spawn entities (evaluating spawnCondition against new flags)
        // 5. spawn player at savepoint, zeroing velocities and resetting previous-position state
        // 6. restore playtimeTicks

        // Prepare initial world with dirty state
        World world = new World();
        RoomStateStore roomStateStore = new RoomStateStore();
        roomStateStore.setInt("old_room", "flag.counter", 42);

        FlagStore flagStore = new FlagStore(registry, roomStateStore);
        flagStore.set(0, 99, 1); // test.stage = 99
        flagStore.set(3, 1, 1);  // session.unlocked = true
        flagStore.set(4, 1, 1);  // temp.active = true
        flagStore.applyPendingChanges("old_room", world.getEventQueue(), 500);

        // Target save state to restore
        Map<String, Object> savedGlobalFlags = Map.of("test.stage", 5); // note: door_open missing, should take default
        Map<String, Map<String, Object>> savedRoomRecords = Map.of(
            "test_01", Map.of("flag.chest_opened", 1)
        );
        SavePayload payload = new SavePayload(
            12345L, "2026-11-02T09:00:00Z", "test_01", "savepoint_3",
            savedGlobalFlags, savedRoomRecords, Collections.emptyMap()
        );
        String saveJson = SaveWriter.serialize(payload, registry);
        SaveEnvelope envelope = SaveLoader.load(saveJson, registry);

        // Execute Swap-Tick Steps
        // Step 1: Replace roomStateStore
        roomStateStore.restoreRoomRecords(envelope.data().roomRecords());
        assertEquals(1, roomStateStore.getInt("test_01", "flag.chest_opened", 0));
        assertEquals(0, roomStateStore.getInt("old_room", "flag.counter", 0), "Old room records must be gone");

        // Step 2 & 3: Set global flags from save, reset session & room_temp
        flagStore.restoreSavedGlobalFlags(envelope.data().globalFlags());
        flagStore.resetSessionAndRoomTemp();
        flagStore.applyPendingChanges("test_01", world.getEventQueue(), 501);

        assertEquals(5, flagStore.get(0, "test_01"), "test.stage restored to 5");
        assertEquals(0, flagStore.get(1, "test_01"), "test.door_open missing took default 0");
        assertEquals(0, flagStore.get(3, "test_01"), "session.unlocked reset to default 0");
        assertEquals(0, flagStore.get(4, "test_01"), "temp.active reset to default 0");

        // Step 4: Load room and spawn entities
        Path roomPath = resolveContentPath("content/rooms/test_01.json");
        Room room = RoomLoader.load(roomPath);
        world.despawnRoomLocalEntities();

        ComponentSchemaRegistry schemaRegistry = ComponentSchemaRegistry.createDefault();
        EntityDefinition spDef = EntityDefinitionLoader.load(resolveContentPath("content/entities/savepoint.json"), schemaRegistry);
        EntityDefinition platDef = EntityDefinitionLoader.load(resolveContentPath("content/entities/moving_platform.json"), schemaRegistry);
        Map<String, EntityDefinition> defs = Map.of("savepoint", spDef, "moving_platform", platDef);

        AtomRegistry atomRegistry = new AtomRegistry();
        EntitySpawner spawner = new EntitySpawner(schemaRegistry);

        for (RoomEntityData ed : room.entities()) {
            if (ed.spawnConditionJson() != null && !ed.spawnConditionJson().isBlank()) {
                var cond = ConditionLoader.parse(
                    new ObjectMapper().readTree(ed.spawnConditionJson()), registry, atomRegistry
                );
                if (!cond.evaluate(flagStore, room.id())) {
                    continue; // Skip spawn per condition
                }
            }
            EntityDefinition def = defs.get(ed.definition());
            if (def != null) {
                spawner.spawn(world, def, ed.instanceId(), ed.x(), ed.y(), ed.overrides());
            }
        }
        world.applyDeferredLifecycle();

        // Verify savepoint_3 entity was spawned
        long spEntityId = world.findEntityByInstanceId("savepoint_3");
        assertNotEquals(com.makeasaying.engine.entity.EntityId.NULL_ID, spEntityId, "savepoint_3 must be spawned");
        SavepointComponent spComp = world.getSavepoint(spEntityId);
        assertNotNull(spComp);
        assertTrue(spComp.active);

        // Step 5: Spawn player at savepoint with zero velocity and reset previous-position state
        BodyComponent spBody = world.getBody(spEntityId);
        long playerId = world.findPlayerEntity();
        if (playerId == com.makeasaying.engine.entity.EntityId.NULL_ID) {
            playerId = world.createEntity();
            world.setController(playerId, new ControllerComponent(ControlSource.PLAYER));
            world.setCollider(playerId, new ColliderComponent(12, 36));
            world.setInputState(playerId, new InputStateComponent(new InputState(actionRegistry)));
            world.setJumpState(playerId, new JumpStateComponent());
            world.setRoomScope(playerId, new RoomScopeComponent(RoomScopeComponent.Scope.PERSISTENT_CARRIED));
        }
        world.setBody(playerId, new BodyComponent(spBody.x, spBody.y));
        BodyComponent playerBody = world.getBody(playerId);
        assertEquals(spBody.x, playerBody.x);
        assertEquals(spBody.y, playerBody.y);
        assertEquals(spBody.x, playerBody.prevX, "prevX must be reset to spawn X");
        assertEquals(spBody.y, playerBody.prevY, "prevY must be reset to spawn Y");
        assertEquals(0.0f, playerBody.vx, "vx must be zeroed");
        assertEquals(0.0f, playerBody.vy, "vy must be zeroed");
        assertEquals(0.0f, playerBody.subpixelX, "subpixelX must be zeroed");
        assertEquals(0.0f, playerBody.subpixelY, "subpixelY must be zeroed");

        // Step 6: Restore playtime ticks
        long restoredPlaytime = envelope.data().playtimeTicks();
        assertEquals(12345L, restoredPlaytime);
    }

    private World createSimWorld() {
        World world = new World();
        long playerId = world.createEntity();
        world.setBody(playerId, new BodyComponent(32, 16));
        world.setCollider(playerId, new ColliderComponent(12, 36));
        world.setInputState(playerId, new InputStateComponent(new InputState(actionRegistry)));
        world.setJumpState(playerId, new JumpStateComponent());
        world.setController(playerId, new ControllerComponent(ControlSource.PLAYER));
        world.setRoomScope(playerId, new RoomScopeComponent(RoomScopeComponent.Scope.PERSISTENT_CARRIED));

        long spId = world.createEntity();
        world.setInstanceId(spId, "savepoint_1");
        world.setBody(spId, new BodyComponent(80, 16));
        world.setCollider(spId, new ColliderComponent(16, 24, -8, 0));
        world.setSavepoint(spId, new SavepointComponent(true));

        world.applyDeferredLifecycle();
        return world;
    }

    private void stepSimulation(World world, FlagStore flagStore, RoomStateStore rss, SavepointSystem sps, long tick) {
        TileGrid grid = new TileGrid(40, 12, 16);
        for (int x = 0; x < 40; x++) {
            grid.setTile(x, 0, TileShape.SOLID);
        }

        PlayerControlSystem controlSystem = new PlayerControlSystem();
        PlatformSystem platformSystem = new PlatformSystem();
        MovementSystem movementSystem = new MovementSystem(MovementTuning.defaultTuning(60));

        long playerId = world.findPlayerEntity();
        InputState input = world.getInputState(playerId).state;

        // Step 2: Input
        controlSystem.update(world, input);

        // Step 4: Platforms
        platformSystem.update(world, grid);

        // Step 5: Movement
        movementSystem.update(world, grid);

        // Step 8: Triggers and Zones (SavepointSystem & Flag drain)
        sps.update(world, tick, false, false, (id, x, y) -> {});
        flagStore.applyPendingChanges("test_01", world.getEventQueue(), tick);

        // Step 9: Event dispatch
        world.getEventQueue().dispatch();

        // Step 11: Deferred lifecycle
        world.applyDeferredLifecycle();
    }
}
