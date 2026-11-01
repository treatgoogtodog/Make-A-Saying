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
        world.applyDeferredLifecycle();

        Room room = new Room("room_01", 40, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("s", 32, 16)),
            Collections.emptyList(),
            List.of(new RoomExit("exit_1", 600, 16, 24, 48, "room_02", "s"))
        );

        RoomTransitionManager transition = new RoomTransitionManager();
        java.util.function.BooleanSupplier alwaysReady = () -> true;
        RoomTransitionManager.SwapCallback noOpSwap = exit -> {};

        // Warm up
        for (int i = 0; i < 5000; i++) {
            transition.update(playerId, world, room, alwaysReady, noOpSwap);
        }

        long threadId = Thread.currentThread().threadId();
        long memBefore = sunThreadBean.getThreadAllocatedBytes(threadId);

        for (int i = 0; i < ITERATIONS; i++) {
            transition.update(playerId, world, room, alwaysReady, noOpSwap);
        }

        long memAfter = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocated = memAfter - memBefore;
        System.out.println("Ordinary ticks allocated bytes over " + ITERATIONS + " iterations: " + allocated);

        assertTrue(allocated < 1024, "Ordinary ticks must have zero heap allocation (was " + allocated + " bytes)");
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
        RoomTransitionConfig config = new RoomTransitionConfig(ITERATIONS * 2, 4, 12);
        RoomTransitionManager transition = new RoomTransitionManager(config);
        RoomExit exit = new RoomExit("exit_1", 32, 16, 24, 48, "room_02", "s");
        transition.startTransition(exit);

        java.util.function.BooleanSupplier alwaysReady = () -> true;
        RoomTransitionManager.SwapCallback noOpSwap = ex -> {};

        // Warm up
        for (int i = 0; i < 5000; i++) {
            transition.update(playerId, world, room, alwaysReady, noOpSwap);
            float alpha = transition.getFadeAlpha();
        }

        long threadId = Thread.currentThread().threadId();
        long memBefore = sunThreadBean.getThreadAllocatedBytes(threadId);

        for (int i = 0; i < ITERATIONS; i++) {
            transition.update(playerId, world, room, alwaysReady, noOpSwap);
            float alpha = transition.getFadeAlpha();
        }

        long memAfter = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocated = memAfter - memBefore;
        System.out.println("Transition ticks allocated bytes over " + ITERATIONS + " iterations: " + allocated);

        assertTrue(allocated < 1024, "Fade ticks must have zero heap allocation (was " + allocated + " bytes)");
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
        RoomTransitionManager transition = new RoomTransitionManager(config);
        RoomExit exit = new RoomExit("exit_1", 32, 16, 24, 48, "room_02", "s");
        transition.startTransition(exit);

        java.util.function.BooleanSupplier notReady = () -> false;
        RoomTransitionManager.SwapCallback noOpSwap = ex -> {};

        // Step into HOLD_WAIT_SWAP
        transition.update(playerId, world, room, notReady, noOpSwap);

        // Warm up
        for (int i = 0; i < 5000; i++) {
            transition.update(playerId, world, room, notReady, noOpSwap);
        }

        long threadId = Thread.currentThread().threadId();
        long memBefore = sunThreadBean.getThreadAllocatedBytes(threadId);

        for (int i = 0; i < ITERATIONS; i++) {
            transition.update(playerId, world, room, notReady, noOpSwap);
        }

        long memAfter = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocated = memAfter - memBefore;
        System.out.println("Suspended ticks allocated bytes over " + ITERATIONS + " iterations: " + allocated);

        assertTrue(allocated < 1024, "Suspended ticks must have zero heap allocation (was " + allocated + " bytes)");
    }
}
