package com.makeasaying.engine.world;

import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.engine.entity.TileShape;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.JumpStateComponent;
import com.makeasaying.engine.entity.components.RoomScopeComponent;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class TransitionDeterminismTest {
    @Test
    void testImmediateVsDelayedPreloadDeterminism() {
        // Setup two identical worlds and transitions
        World worldA = new World();
        World worldB = new World();

        long playerA = worldA.createEntity();
        worldA.setBody(playerA, new BodyComponent(618, 16));
        worldA.setCollider(playerA, new ColliderComponent(12, 36));
        worldA.setJumpState(playerA, new JumpStateComponent());
        worldA.setRoomScope(playerA, new RoomScopeComponent(RoomScopeComponent.Scope.PERSISTENT_CARRIED));
        worldA.applyDeferredLifecycle();

        long playerB = worldB.createEntity();
        worldB.setBody(playerB, new BodyComponent(618, 16));
        worldB.setCollider(playerB, new ColliderComponent(12, 36));
        worldB.setJumpState(playerB, new JumpStateComponent());
        worldB.setRoomScope(playerB, new RoomScopeComponent(RoomScopeComponent.Scope.PERSISTENT_CARRIED));
        worldB.applyDeferredLifecycle();

        Room r1 = new Room("room_01", 40, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("spawn_01", 32, 16)),
            Collections.emptyList(),
            List.of(new RoomExit("to_02", 616, 16, 24, 48, "room_02", "spawn_02"))
        );

        Room r2 = new Room("room_02", 30, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("spawn_02", 48, 16)),
            Collections.emptyList(),
            List.of(new RoomExit("to_01", 0, 16, 24, 48, "room_01", "spawn_01"))
        );

        TileGrid grid1 = new TileGrid(40, 12, 16);
        TileGrid grid2 = new TileGrid(30, 12, 16);
        for (int x = 0; x < 40; x++) grid1.setTile(x, 0, TileShape.SOLID);
        for (int x = 0; x < 30; x++) grid2.setTile(x, 0, TileShape.SOLID);

        RoomStateStore storeA = new RoomStateStore();
        RoomStateStore storeB = new RoomStateStore();

        RoomTransitionConfig config = new RoomTransitionConfig(12, 4, 12);
        RoomTransitionManager transA = new RoomTransitionManager(config);
        RoomTransitionManager transB = new RoomTransitionManager(config);

        // Simulation A: Target room is already ready immediately
        int ticksA = 0;
        String curRoomA = "room_01";
        Room roomObjA = r1;
        TileGrid gridObjA = grid1;

        // Run until transition completes (12 + 4 + 12 = 28 ticks + initial trigger = 29)
        while (transA.getState() != RoomTransitionState.IDLE || ticksA == 0) {
            final String fCurRoom = curRoomA;
            boolean advanced = transA.update(
                playerA, worldA, roomObjA, () -> true,
                exit -> {
                    RoomTransitionHelper.executeRoomSwap(worldA, playerA, r2, exit.targetSpawn(), grid2, storeA);
                }
            );
            if (transA.getState() == RoomTransitionState.FADE_IN && curRoomA.equals("room_01")) {
                curRoomA = "room_02";
                roomObjA = r2;
                gridObjA = grid2;
            }
            if (advanced) ticksA++;
        }

        // Simulation B: Target room is NOT ready initially, delayed by 10 suspended ticks
        int ticksB = 0;
        int suspendedTicks = 0;
        String curRoomB = "room_01";
        Room roomObjB = r1;
        TileGrid gridObjB = grid1;
        AtomicBoolean readyB = new AtomicBoolean(false);

        while (transB.getState() != RoomTransitionState.IDLE || ticksB == 0) {
            // After 10 suspended checks, target becomes ready
            if (transB.isWaitingForLoad()) {
                suspendedTicks++;
                if (suspendedTicks >= 10) {
                    readyB.set(true);
                }
            }

            boolean advanced = transB.update(
                playerB, worldB, roomObjB, readyB::get,
                exit -> {
                    RoomTransitionHelper.executeRoomSwap(worldB, playerB, r2, exit.targetSpawn(), grid2, storeB);
                }
            );
            if (transB.getState() == RoomTransitionState.FADE_IN && curRoomB.equals("room_01")) {
                curRoomB = "room_02";
                roomObjB = r2;
                gridObjB = grid2;
            }
            if (advanced) {
                ticksB++;
            }
        }

        assertEquals(ticksA, ticksB, "Tick counter must advance by exact same amount regardless of loading delays");
        assertEquals(30, ticksA, "Total ticks from exit trigger in IDLE to return to IDLE must be 30 (1 trigger + 12 fade out + 4 hold + 1 swap + 12 fade in)");
        assertEquals(10, suspendedTicks, "Suspended ticks must occur while waiting for worker preload");

        long hashA = GameStateHash.compute(worldA, curRoomA, transA, storeA);
        long hashB = GameStateHash.compute(worldB, curRoomB, transB, storeB);

        assertEquals(hashA, hashB, "GameStateHash must be bit-identical between immediate and delayed loads");
    }

    @Test
    public void testStalledLoaderTimesOutAndAbortsTransition() {
        RoomTransitionConfig config = new RoomTransitionConfig(12, 4, 12);
        long[] fakeTime = new long[] { 1_000_000_000L };
        RoomTransitionManager manager = new RoomTransitionManager(config, () -> fakeTime[0]);

        World world = new World();
        long playerId = world.createEntity();
        world.setBody(playerId, new com.makeasaying.engine.entity.components.BodyComponent(32, 16));
        world.setCollider(playerId, new com.makeasaying.engine.entity.components.ColliderComponent(12, 36));

        RoomExit exit = new RoomExit("exit_1", 20, 0, 24, 48, "stalled_room", "default");
        manager.startTransition(exit);

        // Fade out 12 ticks
        for (int i = 0; i < 12; i++) {
            assertTrue(manager.update(playerId, world, null, () -> false, e -> {}));
        }
        assertEquals(RoomTransitionState.HOLD_WAIT_SWAP, manager.getState());

        // Hold 4 ticks
        for (int i = 0; i < 4; i++) {
            assertTrue(manager.update(playerId, world, null, () -> false, e -> {}));
        }

        // Wait begins at fakeTime = 1s.
        // Call 3 times at 1s, 2s, 5s. Target not ready, time within 10s.
        fakeTime[0] = 1_000_000_000L;
        assertFalse(manager.update(playerId, world, null, () -> false, e -> {}));
        fakeTime[0] = 5_000_000_000L; // +4s
        assertFalse(manager.update(playerId, world, null, () -> false, e -> {}));
        assertTrue(manager.isWaitingForLoad());

        // Now jump time to 11.1s (exceeding 10s monotonic deadline): aborts!
        fakeTime[0] = 11_100_000_000L;
        boolean abortFrameAdvanced = manager.update(playerId, world, null, () -> false, e -> {});
        assertTrue(abortFrameAdvanced, "Aborting must resume frame advance to fade back in");
        assertEquals(RoomTransitionState.FADE_IN, manager.getState());
        assertTrue(manager.isExitDisabled("exit_1"), "Exit must be disabled upon abort");
    }

    @Test
    public void testLoaderFinishingJustBeforeDeadlineSucceedsWithoutAbort() {
        RoomTransitionConfig config = new RoomTransitionConfig(12, 4, 12);
        long[] fakeTime = new long[] { 1_000_000_000L };
        RoomTransitionManager manager = new RoomTransitionManager(config, () -> fakeTime[0]);

        World world = new World();
        long playerId = world.createEntity();
        world.setBody(playerId, new com.makeasaying.engine.entity.components.BodyComponent(32, 16));
        world.setCollider(playerId, new com.makeasaying.engine.entity.components.ColliderComponent(12, 36));

        RoomExit exit = new RoomExit("exit_1", 20, 0, 24, 48, "target_room", "default");
        manager.startTransition(exit);

        for (int i = 0; i < 16; i++) {
            assertTrue(manager.update(playerId, world, null, () -> false, e -> {}));
        }
        assertEquals(RoomTransitionState.HOLD_WAIT_SWAP, manager.getState());

        // First wait check at 1.0s (marks wait start time)
        fakeTime[0] = 1_000_000_000L;
        assertFalse(manager.update(playerId, world, null, () -> false, e -> {}));

        // Advance to 9.9s (elapsed = 8.9s < 10.0s deadline)
        fakeTime[0] = 9_900_000_000L;
        boolean[] swapped = new boolean[1];
        // Now target is ready!
        boolean advanced = manager.update(playerId, world, null, () -> true, e -> { swapped[0] = true; });
        assertTrue(advanced, "Ready target must advance tick into FADE_IN");
        assertTrue(swapped[0], "Swap callback must be executed");
        assertEquals(RoomTransitionState.FADE_IN, manager.getState());
        assertFalse(manager.isExitDisabled("exit_1"), "Successful swap must not disable exit");
    }

    @Test
    public void testBidirectionalTransitionReplayLoopWithPlatforms() {
        // Test bidirectional transition loop room_01 -> room_02 -> room_01 with moving platforms present in both rooms.
        // Compare hash on every tick between immediate and delayed loading.

        Room r1 = new Room("room_01", 40, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("spawn_01", 32, 16)),
            List.of(new RoomEntityData("plat_1", "moving_platform", 100, 32,
                Map.of("platform", Map.of("travelX", 64, "travelY", 0, "speed", 60.0f, "pauseTicks", 10, "crushMode", "BLOCK")))),
            List.of(new RoomExit("to_02", 616, 16, 24, 48, "room_02", "spawn_02"))
        );

        Room r2 = new Room("room_02", 30, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("spawn_02", 48, 16)),
            List.of(new RoomEntityData("plat_2", "moving_platform", 120, 32,
                Map.of("platform", Map.of("travelX", 0, "travelY", 48, "speed", 60.0f, "pauseTicks", 10, "crushMode", "BLOCK")))),
            List.of(new RoomExit("to_01", 0, 16, 24, 48, "room_01", "spawn_01"))
        );

        TileGrid grid1 = new TileGrid(40, 12, 16);
        TileGrid grid2 = new TileGrid(30, 12, 16);
        for (int x = 0; x < 40; x++) grid1.setTile(x, 0, TileShape.SOLID);
        for (int x = 0; x < 30; x++) grid2.setTile(x, 0, TileShape.SOLID);

        com.makeasaying.engine.schema.ComponentSchemaRegistry registry =
            com.makeasaying.engine.schema.ComponentSchemaRegistry.createDefault();
        com.makeasaying.engine.entity.EntitySpawner spawner =
            new com.makeasaying.engine.entity.EntitySpawner(registry);

        com.makeasaying.engine.entity.EntityDefinition platDef = new com.makeasaying.engine.entity.EntityDefinition(
            1, "moving_platform", List.of("platform"), "#4488FF", true,
            Map.of(
                "body", Map.of("x", 0, "y", 0),
                "collider", Map.of("width", 32, "height", 8),
                "platform", Map.of("travelX", 64, "travelY", 0, "speed", 60.0f, "pauseTicks", 10, "crushMode", "BLOCK"),
                "room_scope", Map.of("scope", "ROOM_LOCAL")
            )
        );
        Map<String, com.makeasaying.engine.entity.EntityDefinition> defs = Map.of("moving_platform", platDef);

        com.makeasaying.engine.entity.systems.PlatformSystem platformSystem =
            new com.makeasaying.engine.entity.systems.PlatformSystem();

        java.util.List<Long> hashesA = runBidirectionalSimulation(false, r1, r2, grid1, grid2, spawner, defs, platformSystem);
        java.util.List<Long> hashesB = runBidirectionalSimulation(true, r1, r2, grid1, grid2, spawner, defs, platformSystem);

        assertEquals(hashesA.size(), hashesB.size(), "Total active tick count must match identically across loop");
        for (int i = 0; i < hashesA.size(); i++) {
            assertEquals(hashesA.get(i), hashesB.get(i), "State hash mismatch at active tick " + i);
        }
    }

    private java.util.List<Long> runBidirectionalSimulation(
        boolean delayed,
        Room r1,
        Room r2,
        TileGrid grid1,
        TileGrid grid2,
        com.makeasaying.engine.entity.EntitySpawner spawner,
        Map<String, com.makeasaying.engine.entity.EntityDefinition> defs,
        com.makeasaying.engine.entity.systems.PlatformSystem platformSystem
    ) {
        World world = new World();
        long player = world.createEntity();
        world.setBody(player, new BodyComponent(618, 16));
        world.setCollider(player, new ColliderComponent(12, 36));
        world.setJumpState(player, new JumpStateComponent());
        world.setRoomScope(player, new RoomScopeComponent(RoomScopeComponent.Scope.PERSISTENT_CARRIED));

        // Initial entities in r1
        for (RoomEntityData ed : r1.entities()) {
            spawner.spawn(world, defs.get(ed.definition()), ed.instanceId(), ed.x(), ed.y(), ed.overrides());
        }
        world.applyDeferredLifecycle();

        RoomStateStore store = new RoomStateStore();
        RoomTransitionConfig config = new RoomTransitionConfig(12, 4, 12);
        RoomTransitionManager trans = new RoomTransitionManager(config);

        java.util.List<Long> hashes = new java.util.ArrayList<>();

        String curRoomId = "room_01";
        Room curRoom = r1;
        TileGrid curGrid = grid1;

        int delaysLeft = 0;

        // Sequence:
        // 1. In room_01: Player is at exit (618, 16), trigger transition to room_02 (ticks to complete = 30)
        // 2. In room_02: Stay 20 ticks, platform_2 moves. Then move player to exit (0, 16)
        // 3. Trigger transition to room_01 (ticks to complete = 30)
        // 4. In room_01: Stay 20 ticks, platform_1 moves.
        int phase = 0; // 0: trans to r2, 1: stay in r2, 2: trans to r1, 3: stay in r1
        int phaseTicks = 0;

        while (phase < 4) {
            final String activeRoomId = curRoomId;
            final Room activeRoom = curRoom;
            final TileGrid activeGrid = curGrid;

            boolean isWaiting = trans.isWaitingForLoad();
            if (isWaiting && delayed && delaysLeft > 0) {
                delaysLeft--;
                // Target not ready yet; tick loop suspended
                trans.update(player, world, activeRoom, () -> false, e -> {});
                continue;
            }

            boolean advanced = trans.update(
                player, world, activeRoom,
                () -> true,
                exit -> {
                    Room targetRoom = exit.targetRoom().equals("room_02") ? r2 : r1;
                    TileGrid targetGrid = exit.targetRoom().equals("room_02") ? grid2 : grid1;
                    RoomTransitionHelper.executeRoomSwap(world, player, targetRoom, exit.targetSpawn(), targetGrid, store);
                    // Despawn local entities and respawn target room local entities
                    world.despawnRoomLocalEntities();
                    world.applyDeferredLifecycle();
                    for (RoomEntityData ed : targetRoom.entities()) {
                        spawner.spawn(world, defs.get(ed.definition()), ed.instanceId(), ed.x(), ed.y(), ed.overrides());
                    }
                    world.applyDeferredLifecycle();
                }
            );

            if (trans.getState() == RoomTransitionState.FADE_IN) {
                if (curRoomId.equals("room_01") && phase == 0) {
                    curRoomId = "room_02";
                    curRoom = r2;
                    curGrid = grid2;
                } else if (curRoomId.equals("room_02") && phase == 2) {
                    curRoomId = "room_01";
                    curRoom = r1;
                    curGrid = grid1;
                }
            }

            if (advanced) {
                // When in IDLE (gameplay active), platforms simulate
                if (trans.getState() == RoomTransitionState.IDLE) {
                    platformSystem.update(world, curGrid);

                    if (phase == 0) {
                        // Just finished transition to r2
                        phase = 1;
                        phaseTicks = 0;
                    } else if (phase == 1) {
                        phaseTicks++;
                        if (phaseTicks >= 20) {
                            // Move player to exit in r2 to trigger transition back to r1
                            world.getBody(player).x = 0;
                            world.getBody(player).y = 16;
                            phase = 2;
                            phaseTicks = 0;
                            delaysLeft = delayed ? 5 : 0;
                        }
                    } else if (phase == 2) {
                        // Just finished transition to r1
                        phase = 3;
                        phaseTicks = 0;
                    } else if (phase == 3) {
                        phaseTicks++;
                        if (phaseTicks >= 20) {
                            phase = 4; // done
                        }
                    }
                } else if (phase == 0 && trans.getState() == RoomTransitionState.FADE_OUT && delaysLeft == 0 && delayed) {
                    delaysLeft = 5;
                }

                hashes.add(GameStateHash.compute(world, curRoomId, trans, store));
            }
        }

        return hashes;
    }

    @Test
    void testReplayHashAcrossRecordedFlagChangeAndRoomSwapEveryTick() {
        // Setup world, flag store, transition manager
        com.makeasaying.engine.flags.FlagDefinition defStage =
            new com.makeasaying.engine.flags.FlagDefinition(0, "test.stage", com.makeasaying.engine.flags.FlagType.INT, com.makeasaying.engine.flags.FlagScope.GLOBAL, 0, 0, 10, null, "doc");
        com.makeasaying.engine.flags.FlagRegistry registry = new com.makeasaying.engine.flags.FlagRegistry(List.of(defStage));

        List<Long> liveHashes = runFlagAndTransitionSequence(registry, false);
        List<Long> replayHashes = runFlagAndTransitionSequence(registry, false);

        assertEquals(liveHashes.size(), replayHashes.size());
        for (int t = 0; t < liveHashes.size(); t++) {
            assertEquals(liveHashes.get(t), replayHashes.get(t),
                "Tick " + t + ": Replay hash must match live run bit-for-bit across flag changes and room swap");
        }
    }

    private List<Long> runFlagAndTransitionSequence(com.makeasaying.engine.flags.FlagRegistry registry, boolean dummy) {
        World world = new World();
        long player = world.createEntity();
        world.setBody(player, new BodyComponent(100, 16));
        world.setCollider(player, new ColliderComponent(12, 36));
        world.setJumpState(player, new JumpStateComponent());
        world.setRoomScope(player, new RoomScopeComponent(RoomScopeComponent.Scope.PERSISTENT_CARRIED));
        world.applyDeferredLifecycle();

        Room r1 = new Room("room_01", 30, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("spawn_01", 32, 16)),
            Collections.emptyList(),
            List.of(new RoomExit("to_02", 200, 16, 24, 48, "room_02", "spawn_02"))
        );
        Room r2 = new Room("room_02", 30, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("spawn_02", 32, 16)),
            Collections.emptyList(),
            Collections.emptyList()
        );

        TileGrid grid = new TileGrid(30, 12, 16);
        for (int x = 0; x < 30; x++) grid.setTile(x, 0, TileShape.SOLID);

        RoomStateStore store = new RoomStateStore();
        com.makeasaying.engine.flags.FlagStore flagStore = new com.makeasaying.engine.flags.FlagStore(registry, store);
        RoomTransitionManager trans = new RoomTransitionManager(new RoomTransitionConfig(12, 4, 12));

        String curRoomId = "room_01";
        Room curRoom = r1;
        List<Long> hashes = new java.util.ArrayList<>();

        for (int tick = 0; tick < 80; tick++) {
            final int currentTick = tick;
            // At tick 10, mutate flag
            if (tick == 10) {
                flagStore.set(0, 3, com.makeasaying.engine.flags.FlagChangeLogEntry.SOURCE_GAMEPLAY);
            }
            flagStore.applyPendingChanges(curRoomId, world.getEventQueue(), tick);

            // At tick 15, trigger transition by placing player in exit
            if (tick == 15) {
                world.getBody(player).x = 200;
            }

            boolean advanced = trans.update(player, world, curRoom,
                () -> true,
                targetRoom -> {
                    // Swap tick
                    flagStore.clearRoomTemp(currentTick);
                }
            );

            if (trans.getState() == RoomTransitionState.FADE_IN && curRoomId.equals("room_01")) {
                curRoomId = "room_02";
                curRoom = r2;
            }

            if (advanced) {
                long stateHash = GameStateHash.compute(world, curRoomId, trans, store) ^ flagStore.computeStateHash(curRoomId);
                hashes.add(stateHash);
            }
        }

        return hashes;
    }
}
