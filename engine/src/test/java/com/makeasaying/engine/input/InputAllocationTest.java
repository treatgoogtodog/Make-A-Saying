package com.makeasaying.engine.input;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InputAllocationTest {

    @Test
    void testZeroAllocationsInInputDrainAndStateAccess() {
        java.lang.management.ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (!(threadBean instanceof com.sun.management.ThreadMXBean sunThreadBean)) {
            return;
        }
        if (!sunThreadBean.isThreadAllocatedMemorySupported()) {
            return;
        }

        ActionRegistry registry = new ActionRegistry();
        registry.register(ActionDefinition.button("move_left"));
        registry.register(ActionDefinition.button("move_right"));
        registry.register(ActionDefinition.button("jump"));
        registry.register(ActionDefinition.axis("move_x", "move_left", "move_right"));

        ContextBindings bindings = new ContextBindings();
        bindings.bind(InputContext.GAMEPLAY, "move_left", Input.Keys.A);
        bindings.bind(InputContext.GAMEPLAY, "move_right", Input.Keys.D);
        bindings.bind(InputContext.GAMEPLAY, "jump", Input.Keys.SPACE);

        InputManager manager = new InputManager(registry, bindings);
        InputState state = manager.getState();

        long threadId = Thread.currentThread().threadId();

        // 1. Warm-up
        for (int i = 0; i < 5000; i++) {
            if (i % 2 == 0) manager.keyDown(Input.Keys.A);
            else manager.keyUp(Input.Keys.A);
            manager.tick(i, true);
            state.getAxis("move_x");
            state.isDown("jump");
            state.isPressed("move_left");
        }

        // 2. Measure allocated memory over 100,000 tick drains
        long bytesBefore = sunThreadBean.getThreadAllocatedBytes(threadId);

        int iterations = 100_000;
        int dummyAccumulator = 0;
        for (int i = 0; i < iterations; i++) {
            manager.tick(i, true);
            dummyAccumulator += state.getAxis("move_x");
            if (state.isDown("jump")) dummyAccumulator++;
            if (state.isPressed("move_left")) dummyAccumulator++;
        }

        long bytesAfter = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocatedBytes = bytesAfter - bytesBefore;

        assertTrue(dummyAccumulator != 0 || dummyAccumulator == 0);
        assertTrue(allocatedBytes < 1024,
            "Input drain and state access in hot loop must not allocate memory! Allocated bytes across 100k ticks: " + allocatedBytes);
    }
}
