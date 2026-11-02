package com.makeasaying.game;

import com.makeasaying.engine.entity.MovementTuning;
import com.makeasaying.engine.entity.PlayerFactory;
import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.components.PlatformComponent;
import com.makeasaying.engine.entity.components.RoomScopeComponent;
import com.makeasaying.engine.entity.systems.MovementSystem;
import com.makeasaying.engine.entity.systems.PlatformSystem;
import com.makeasaying.engine.entity.systems.PlayerControlSystem;
import com.makeasaying.engine.input.InputState;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameModeAllocationTest {
    private static final int ITERATIONS = 100000;

    @Test
    void testGameModeOrdinaryTicksZeroAllocationWithEditorOnClasspath() {
        java.lang.management.ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (!(threadBean instanceof com.sun.management.ThreadMXBean sunThreadBean) ||
            !sunThreadBean.isThreadAllocatedMemorySupported()) {
            return;
        }

        World world = new World();
        TileGrid grid = new TileGrid(40, 20, 16);
        MovementTuning tuning = MovementTuning.defaultTuning(60);
        MovementSystem movementSystem = new MovementSystem(tuning);
        PlatformSystem platformSystem = new PlatformSystem();
        PlayerControlSystem controlSystem = new PlayerControlSystem();

        long playerId = PlayerFactory.createPlayer(world, 100, 100);

        // Moving platform
        long platId = world.createEntity();
        world.setPlatform(platId, new PlatformComponent(60, 40, 140, 40, 48, 16, 2.0f, 10, PlatformComponent.CrushMode.BLOCK));
        world.setRoomScope(platId, new RoomScopeComponent(RoomScopeComponent.Scope.ROOM_LOCAL));
        world.applyDeferredLifecycle();

        com.makeasaying.engine.input.ActionRegistry registry = new com.makeasaying.engine.input.ActionRegistry();
        InputState activeState = new InputState(registry);

        long threadId = Thread.currentThread().threadId();
        sunThreadBean.getThreadAllocatedBytes(threadId); // Prime ThreadMXBean

        // Warm up with 200k ticks to ensure full C2 compilation and On-Stack-Replacement (OSR)
        for (int i = 0; i < 200000; i++) {
            controlSystem.update(world, activeState);
            platformSystem.update(world, grid);
            movementSystem.update(world, grid);
            world.getEventQueue().dispatch();
            world.applyDeferredLifecycle();
        }

        // Window 1: 100k ticks
        long memBefore1 = sunThreadBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < ITERATIONS; i++) {
            controlSystem.update(world, activeState);
            platformSystem.update(world, grid);
            movementSystem.update(world, grid);
            world.getEventQueue().dispatch();
            world.applyDeferredLifecycle();
        }
        long memAfter1 = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocated1 = memAfter1 - memBefore1;

        // Window 2: 100k ticks
        long memBefore2 = sunThreadBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < ITERATIONS; i++) {
            controlSystem.update(world, activeState);
            platformSystem.update(world, grid);
            movementSystem.update(world, grid);
            world.getEventQueue().dispatch();
            world.applyDeferredLifecycle();
        }
        long memAfter2 = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocated2 = memAfter2 - memBefore2;

        System.out.println("Game-mode ordinary ticks window 1 (100k): " + allocated1 + " bytes, window 2 (100k): " + allocated2 + " bytes");

        assertEquals(0, allocated1, "Game mode ordinary ticks window 1 must be strictly 0 bytes (was " + allocated1 + " bytes)");
        assertEquals(0, allocated2, "Game mode ordinary ticks window 2 must be strictly 0 bytes (was " + allocated2 + " bytes)");
    }
}
