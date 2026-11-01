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
        RoomTransitionManager manager = new RoomTransitionManager(config);

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

        // Target is checked and not ready: returns false and increments wait timeout
        for (int i = 0; i < RoomTransitionManager.TIMEOUT_TICKS; i++) {
            boolean advanced = manager.update(playerId, world, null, () -> false, e -> {});
            assertFalse(advanced, "Tick advance must remain suspended while waiting");
            assertTrue(manager.isWaitingForLoad());
        }

        // 601st wait call exceeds timeout: aborts transition and fades back in!
        boolean abortFrameAdvanced = manager.update(playerId, world, null, () -> false, e -> {});
        assertTrue(abortFrameAdvanced, "Aborting must resume frame advance to fade back in");
        assertEquals(RoomTransitionState.FADE_IN, manager.getState());
        assertTrue(manager.isExitDisabled("exit_1"), "Exit must be disabled upon abort");
    }
}
