package com.makeasaying.engine.entity;

import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.ControllerComponent;
import com.makeasaying.engine.entity.components.FacingComponent;
import com.makeasaying.engine.entity.components.InputStateComponent;
import com.makeasaying.engine.entity.components.JumpStateComponent;
import com.makeasaying.engine.entity.systems.MovementSystem;
import com.makeasaying.engine.entity.systems.PlayerControlSystem;
import com.makeasaying.engine.input.ActionDefinition;
import com.makeasaying.engine.input.ActionRegistry;
import com.makeasaying.engine.input.Actions;
import com.makeasaying.engine.input.InputState;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityAllocationTest {

    @Test
    void testZeroAllocationsInHotEntitySimulationLoop() {
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
        registry.register(ActionDefinition.button(Actions.JUMP));
        registry.register(ActionDefinition.axis(Actions.MOVE_X, Actions.MOVE_LEFT, Actions.MOVE_RIGHT));

        MovementTuning tuning = MovementTuning.defaultTuning(60);
        TileGrid grid = new TileGrid(30, 20, 16);
        for (int x = 0; x < 30; x++) grid.setTile(x, 0, TileShape.SOLID);

        World world = new World();
        PlayerControlSystem controlSystem = new PlayerControlSystem();
        MovementSystem movementSystem = new MovementSystem(tuning);

        long id = world.createEntity();
        world.setBody(id, new BodyComponent(64, 16));
        world.setCollider(id, new ColliderComponent(12, 36));
        world.setFacing(id, new FacingComponent(1));
        world.setController(id, new ControllerComponent(ControlSource.PLAYER));
        world.setJumpState(id, new JumpStateComponent());
        world.setInputState(id, new InputStateComponent(new InputState(registry)));
        world.applyDeferredLifecycle();

        InputState liveInput = new InputState(registry);
        liveInput.setButtonDown(Actions.MOVE_RIGHT, true);
        liveInput.resolveAxes();

        long threadId = Thread.currentThread().threadId();

        // 1. Warm-up
        for (int i = 0; i < 5000; i++) {
            controlSystem.update(world, liveInput);
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
            movementSystem.update(world, grid);
            world.getEventQueue().dispatch();
            world.applyDeferredLifecycle();

            BodyComponent b = world.getBody(id);
            if (b != null) dummyAccumulator += b.x;
        }

        long bytesAfter = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocatedBytes = bytesAfter - bytesBefore;

        assertTrue(dummyAccumulator != 0 || dummyAccumulator == 0);
        assertTrue(allocatedBytes < 1024,
            "Full entity simulation loop must not allocate memory per tick! Allocated bytes across 100k ticks: " + allocatedBytes);
    }
}
