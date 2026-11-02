package com.makeasaying.data.world;

import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.RoomScopeComponent;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomExit;
import com.makeasaying.engine.world.RoomTransitionConfig;
import com.makeasaying.engine.world.RoomTransitionManager;
import com.makeasaying.engine.world.SpawnPoint;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M5bAllocationTest {
    private static final int ITERATIONS = 100000;

    @Test
    void testOrdinaryTicksAllocation() {
        java.lang.management.ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (!(threadBean instanceof com.sun.management.ThreadMXBean sunThreadBean) ||
            !sunThreadBean.isThreadAllocatedMemorySupported()) {
            return;
        }

        World world = new World();
        long playerId = world.createEntity();
        world.setBody(playerId, new BodyComponent(32, 16));
        world.setCollider(playerId, new ColliderComponent(12, 36));
        world.setRoomScope(playerId, new RoomScopeComponent(RoomScopeComponent.Scope.PERSISTENT_CARRIED));

        // Add a moving platform so entities are present in world
        long platId = world.createEntity();
        world.setPlatform(platId, new com.makeasaying.engine.entity.components.PlatformComponent(
            64, 32, 128, 32, 32, 8, 1.0f, 10, com.makeasaying.engine.entity.components.PlatformComponent.CrushMode.BLOCK));
        world.setRoomScope(platId, new RoomScopeComponent(RoomScopeComponent.Scope.ROOM_LOCAL));
        world.applyDeferredLifecycle();

        Room room = new Room("room_01", 40, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("s", 32, 16)),
            Collections.emptyList(),
            List.of(new RoomExit("exit_1", 600, 16, 24, 48, "room_02", "s"))
        );

        RoomTransitionManager transition = new RoomTransitionManager();
        java.util.function.BooleanSupplier alwaysReady = () -> true;
        RoomTransitionManager.SwapCallback noOpSwap = exit -> {};

        long threadId = Thread.currentThread().threadId();
        sunThreadBean.getThreadAllocatedBytes(threadId); // Prime ThreadMXBean

        // Warm up with at least 25k ticks to ensure full C2 compilation
        for (int i = 0; i < 25000; i++) {
            transition.update(playerId, world, room, alwaysReady, noOpSwap);
        }

        // Window 1: 100k iterations
        long memBefore1 = sunThreadBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < ITERATIONS; i++) {
            transition.update(playerId, world, room, alwaysReady, noOpSwap);
        }
        long memAfter1 = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocated1 = memAfter1 - memBefore1;

        // Window 2: 100k iterations
        long memBefore2 = sunThreadBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < ITERATIONS; i++) {
            transition.update(playerId, world, room, alwaysReady, noOpSwap);
        }
        long memAfter2 = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocated2 = memAfter2 - memBefore2;

        System.out.println("Ordinary ticks window 1 (100k): " + allocated1 + " bytes, window 2 (100k): " + allocated2 + " bytes");

        assertEquals(0, allocated1, "Ordinary ticks window 1 must be strictly 0 bytes (was " + allocated1 + " bytes)");
        assertEquals(0, allocated2, "Ordinary ticks window 2 must be strictly 0 bytes (was " + allocated2 + " bytes)");
    }

    @Test
    void testTransitionTicksAllocation() {
        java.lang.management.ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (!(threadBean instanceof com.sun.management.ThreadMXBean sunThreadBean) ||
            !sunThreadBean.isThreadAllocatedMemorySupported()) {
            return;
        }

        World world = new World();
        long playerId = world.createEntity();
        world.setBody(playerId, new BodyComponent(32, 16));
        world.setCollider(playerId, new ColliderComponent(12, 36));
        world.applyDeferredLifecycle();

        Room room = new Room("room_01", 40, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("s", 32, 16)),
            Collections.emptyList(),
            Collections.emptyList()
        );

        // Huge transition duration so it stays in transition throughout iterations
        RoomTransitionConfig config = new RoomTransitionConfig(ITERATIONS * 6, 4, 12);
        RoomTransitionManager transition = new RoomTransitionManager(config);
        RoomExit exit = new RoomExit("exit_1", 32, 16, 24, 48, "room_02", "s");
        transition.startTransition(exit);

        java.util.function.BooleanSupplier alwaysReady = () -> true;
        RoomTransitionManager.SwapCallback noOpSwap = ex -> {};

        long threadId = Thread.currentThread().threadId();
        sunThreadBean.getThreadAllocatedBytes(threadId); // Prime ThreadMXBean

        // Warm up with 300k ticks to ensure full C2 compilation and all tier transitions complete
        for (int i = 0; i < 300000; i++) {
            transition.update(playerId, world, room, alwaysReady, noOpSwap);
            float alpha = transition.getFadeAlpha();
        }

        // Window 1: 100k iterations
        long memBefore1 = sunThreadBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < ITERATIONS; i++) {
            transition.update(playerId, world, room, alwaysReady, noOpSwap);
            float alpha = transition.getFadeAlpha();
        }
        long memAfter1 = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocated1 = memAfter1 - memBefore1;

        // Window 2: 100k iterations
        long memBefore2 = sunThreadBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < ITERATIONS; i++) {
            transition.update(playerId, world, room, alwaysReady, noOpSwap);
            float alpha = transition.getFadeAlpha();
        }
        long memAfter2 = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocated2 = memAfter2 - memBefore2;

        System.out.println("Transition ticks window 1 (100k): " + allocated1 + " bytes, window 2 (100k): " + allocated2 + " bytes");

        assertEquals(0, allocated1, "Fade ticks window 1 must be strictly 0 bytes (was " + allocated1 + " bytes)");
        assertEquals(0, allocated2, "Fade ticks window 2 must be strictly 0 bytes (was " + allocated2 + " bytes)");
    }

    @Test
    void testSuspendedTicksAllocation() {
        java.lang.management.ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (!(threadBean instanceof com.sun.management.ThreadMXBean sunThreadBean) ||
            !sunThreadBean.isThreadAllocatedMemorySupported()) {
            return;
        }

        World world = new World();
        long playerId = world.createEntity();
        world.setBody(playerId, new BodyComponent(32, 16));
        world.setCollider(playerId, new ColliderComponent(12, 36));
        world.applyDeferredLifecycle();

        Room room = new Room("room_01", 40, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("s", 32, 16)),
            Collections.emptyList(),
            Collections.emptyList()
        );

        RoomTransitionConfig config = new RoomTransitionConfig(0, 0, 12);
        // Supply constant monotonic clock so timeout never fires during allocation loop
        RoomTransitionManager transition = new RoomTransitionManager(config, () -> 1000L);
        RoomExit exit = new RoomExit("exit_1", 32, 16, 24, 48, "room_02", "s");
        transition.startTransition(exit);

        java.util.function.BooleanSupplier notReady = () -> false;
        RoomTransitionManager.SwapCallback noOpSwap = ex -> {};

        // Step into HOLD_WAIT_SWAP
        transition.update(playerId, world, room, notReady, noOpSwap);

        long threadId = Thread.currentThread().threadId();
        sunThreadBean.getThreadAllocatedBytes(threadId); // Prime ThreadMXBean

        // Warm up with 25k ticks
        for (int i = 0; i < 25000; i++) {
            transition.update(playerId, world, room, notReady, noOpSwap);
        }

        // Window 1: 100k iterations
        long memBefore1 = sunThreadBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < ITERATIONS; i++) {
            transition.update(playerId, world, room, notReady, noOpSwap);
        }
        long memAfter1 = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocated1 = memAfter1 - memBefore1;

        // Window 2: 100k iterations
        long memBefore2 = sunThreadBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < ITERATIONS; i++) {
            transition.update(playerId, world, room, notReady, noOpSwap);
        }
        long memAfter2 = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocated2 = memAfter2 - memBefore2;

        System.out.println("Suspended ticks window 1 (100k): " + allocated1 + " bytes, window 2 (100k): " + allocated2 + " bytes");

        assertEquals(0, allocated1, "Suspended ticks window 1 must be strictly 0 bytes (was " + allocated1 + " bytes)");
        assertEquals(0, allocated2, "Suspended ticks window 2 must be strictly 0 bytes (was " + allocated2 + " bytes)");
    }
}
