package com.makeasaying.engine.entity;

import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.ControllerComponent;
import com.makeasaying.engine.entity.components.FacingComponent;
import com.makeasaying.engine.entity.components.InputStateComponent;
import com.makeasaying.engine.entity.components.JumpStateComponent;
import com.makeasaying.engine.entity.components.PlatformComponent;
import com.makeasaying.engine.entity.systems.MovementSystem;
import com.makeasaying.engine.entity.systems.PlatformSystem;
import com.makeasaying.engine.entity.systems.PlayerControlSystem;
import com.makeasaying.engine.input.ActionDefinition;
import com.makeasaying.engine.input.ActionRegistry;
import com.makeasaying.engine.input.Actions;
import com.makeasaying.engine.input.InputState;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

class M4AllocationTest {

    @Test
    void testZeroAllocationsInM4SimulationLoopWithSlopesAndPlatforms() {
        java.lang.management.ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (!(threadBean instanceof com.sun.management.ThreadMXBean sunThreadBean)) {
            return;
        }
        if (!sunThreadBean.isThreadAllocatedMemorySupported()) {
            return;
        }

        ActionRegistry registry = new ActionRegistry();
        registry.register(ActionDefinition.button(Actions.MOVE_LEFT));
        registry.register(ActionDefinition.button(Actions.MOVE_RIGHT));
        registry.register(ActionDefinition.button(Actions.MOVE_DOWN));
        registry.register(ActionDefinition.button(Actions.JUMP));
        registry.register(ActionDefinition.axis(Actions.MOVE_X, Actions.MOVE_LEFT, Actions.MOVE_RIGHT));
        registry.register(ActionDefinition.axis(Actions.MOVE_Y, Actions.MOVE_DOWN, Actions.MOVE_UP));

        MovementTuning tuning = MovementTuning.defaultTuning(60);
        TileGrid grid = new TileGrid(40, 20, 16);
        for (int x = 0; x < 40; x++) grid.setTile(x, 0, TileShape.SOLID);
        grid.setTile(5, 1, TileShape.SLOPE_45_UP_RIGHT);
        grid.setTile(10, 2, TileShape.ONE_WAY);

        World world = new World();
        PlayerControlSystem controlSystem = new PlayerControlSystem();
        PlatformSystem platformSystem = new PlatformSystem();
        MovementSystem movementSystem = new MovementSystem(tuning);

        long playerId = world.createEntity();
        world.setBody(playerId, new BodyComponent(32, 16));
        world.setCollider(playerId, new ColliderComponent(12, 36));
        world.setFacing(playerId, new FacingComponent(1));
        world.setController(playerId, new ControllerComponent(ControlSource.PLAYER));
        world.setJumpState(playerId, new JumpStateComponent());
        world.setInputState(playerId, new InputStateComponent(new InputState(registry)));

        long platId = world.createEntity();
        world.setPlatform(platId, new PlatformComponent(64, 32, 120, 32, 48, 16, 2.0f));
        world.applyDeferredLifecycle();

        InputState liveInput = new InputState(registry);
        liveInput.setButtonDown(Actions.MOVE_RIGHT, true);
        liveInput.resolveAxes();

        long threadId = Thread.currentThread().threadId();

        // 1. Warm-up
        for (int i = 0; i < 5000; i++) {
            controlSystem.update(world, liveInput);
            platformSystem.update(world, grid);
            movementSystem.update(world, grid);
            world.getEventQueue().dispatch();
            world.applyDeferredLifecycle();
        }

        // 2. Measure allocated memory over 100,000 entity ticks
        long bytesBefore = sunThreadBean.getThreadAllocatedBytes(threadId);

        int iterations = 100_000;
        int dummyAccumulator = 0;
        for (int i = 0; i < iterations; i++) {
            controlSystem.update(world, liveInput);
            platformSystem.update(world, grid);
            movementSystem.update(world, grid);
            world.getEventQueue().dispatch();
            world.applyDeferredLifecycle();

            BodyComponent b = world.getBody(playerId);
            if (b != null) dummyAccumulator += b.x;
        }

        long bytesAfter = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocatedBytes = bytesAfter - bytesBefore;

        assertTrue(dummyAccumulator != 0 || dummyAccumulator == 0);
        assertTrue(allocatedBytes < 1024,
            "M4 simulation loop with slopes and moving platforms must not allocate memory per tick! Allocated bytes across 100k ticks: " + allocatedBytes);
    }
}
