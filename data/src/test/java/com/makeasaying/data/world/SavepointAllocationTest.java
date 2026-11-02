package com.makeasaying.data.world;

import com.makeasaying.engine.entity.ControlSource;
import com.makeasaying.engine.entity.MovementTuning;
import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.engine.entity.TileShape;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.ControllerComponent;
import com.makeasaying.engine.entity.components.InputStateComponent;
import com.makeasaying.engine.entity.components.JumpStateComponent;
import com.makeasaying.engine.entity.components.RoomScopeComponent;
import com.makeasaying.engine.entity.components.SavepointComponent;
import com.makeasaying.engine.entity.events.EntityEventQueue;
import com.makeasaying.engine.entity.systems.MovementSystem;
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
import com.makeasaying.engine.world.RoomStateStore;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SavepointAllocationTest {
    private static final int ITERATIONS = 100000;

    @Test
    void testZeroAllocationWithSavepointPresentAndPlayerOverlapping() {
        java.lang.management.ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (!(threadBean instanceof com.sun.management.ThreadMXBean sunThreadBean) ||
            !sunThreadBean.isThreadAllocatedMemorySupported()) {
            return;
        }

        ActionRegistry actionRegistry = new ActionRegistry();
        actionRegistry.register(ActionDefinition.button(Actions.MOVE_LEFT));
        actionRegistry.register(ActionDefinition.button(Actions.MOVE_RIGHT));
        actionRegistry.register(ActionDefinition.button(Actions.MOVE_DOWN));
        actionRegistry.register(ActionDefinition.button(Actions.MOVE_UP));
        actionRegistry.register(ActionDefinition.button(Actions.JUMP));
        actionRegistry.register(ActionDefinition.button(Actions.INTERACT));
        actionRegistry.register(ActionDefinition.axis(Actions.MOVE_X, Actions.MOVE_LEFT, Actions.MOVE_RIGHT));
        actionRegistry.register(ActionDefinition.axis(Actions.MOVE_Y, Actions.MOVE_DOWN, Actions.MOVE_UP));

        World world = new World();
        // Player placed at (80, 16)
        long playerId = world.createEntity();
        world.setBody(playerId, new BodyComponent(80, 16));
        world.setCollider(playerId, new ColliderComponent(12, 36));
        world.setInputState(playerId, new InputStateComponent(new InputState(actionRegistry)));
        world.setJumpState(playerId, new JumpStateComponent());
        world.setController(playerId, new ControllerComponent(ControlSource.PLAYER));
        world.setRoomScope(playerId, new RoomScopeComponent(RoomScopeComponent.Scope.PERSISTENT_CARRIED));

        // Savepoint placed at (80, 16) with collider [16x24, -8, 0]
        long spId = world.createEntity();
        world.setInstanceId(spId, "savepoint_3");
        world.setBody(spId, new BodyComponent(80, 16));
        world.setCollider(spId, new ColliderComponent(16, 24, -8, 0));
        world.setSavepoint(spId, new SavepointComponent(true));

        world.applyDeferredLifecycle();

        TileGrid grid = new TileGrid(40, 12, 16);
        for (int x = 0; x < 40; x++) {
            grid.setTile(x, 0, TileShape.SOLID);
        }

        FlagDefinition fStage = new FlagDefinition(0, "test.stage", FlagType.INT, FlagScope.GLOBAL, 0, 0, 100, null, "doc");
        FlagRegistry registry = new FlagRegistry(List.of(fStage));
        RoomStateStore roomStateStore = new RoomStateStore();
        FlagStore flagStore = new FlagStore(registry, roomStateStore);

        PlayerControlSystem controlSystem = new PlayerControlSystem();
        MovementSystem movementSystem = new MovementSystem(MovementTuning.defaultTuning(60));
        SavepointSystem savepointSystem = new SavepointSystem();
        InputState input = new InputState(actionRegistry);
        EntityEventQueue eventQueue = world.getEventQueue();

        // Verify player is indeed overlapping the savepoint
        assertTrue(savepointSystem.isPlayerOverlappingSavepoint(world),
            "Player must be overlapping savepoint for allocation verification");

        SavepointSystem.SaveTriggerCallback callback = (id, x, y) -> {};

        long threadId = Thread.currentThread().threadId();
        sunThreadBean.getThreadAllocatedBytes(threadId); // Prime

        // Warm up with 50k ticks
        for (int i = 0; i < 50000; i++) {
            runSimulationTick(world, playerId, controlSystem, movementSystem, savepointSystem, grid, input, flagStore, eventQueue, callback, i);
        }

        // Window 1: 100k ticks
        long memBefore1 = sunThreadBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < ITERATIONS; i++) {
            runSimulationTick(world, playerId, controlSystem, movementSystem, savepointSystem, grid, input, flagStore, eventQueue, callback, 50000 + i);
        }
        long memAfter1 = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocated1 = memAfter1 - memBefore1;

        // Window 2: 100k ticks
        long memBefore2 = sunThreadBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < ITERATIONS; i++) {
            runSimulationTick(world, playerId, controlSystem, movementSystem, savepointSystem, grid, input, flagStore, eventQueue, callback, 150000 + i);
        }
        long memAfter2 = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocated2 = memAfter2 - memBefore2;

        System.out.println("Savepoint present simulation ticks window 1 (100k): " + allocated1 + " bytes, window 2 (100k): " + allocated2 + " bytes");

        assertEquals(0, allocated1, "Savepoint overlap sim ticks window 1 must be strictly 0 bytes (was " + allocated1 + " bytes)");
        assertEquals(0, allocated2, "Savepoint overlap sim ticks window 2 must be strictly 0 bytes (was " + allocated2 + " bytes)");
    }

    private void runSimulationTick(
        World world,
        long playerId,
        PlayerControlSystem controlSystem,
        MovementSystem movementSystem,
        SavepointSystem savepointSystem,
        TileGrid grid,
        InputState input,
        FlagStore flagStore,
        EntityEventQueue eventQueue,
        SavepointSystem.SaveTriggerCallback callback,
        long tick
    ) {
        // Step 2: Input
        controlSystem.update(world, input);

        // Step 5: Movement
        movementSystem.update(world, grid);

        // Step 8: Triggers and Zones - Prompt check and savepoint update
        boolean promptVisible = savepointSystem.isPlayerOverlappingSavepoint(world);
        savepointSystem.update(world, tick, false, false, callback);
        flagStore.applyPendingChanges("test_01", eventQueue, tick);

        // Step 9: Event dispatch
        eventQueue.dispatch();

        // Step 11: Deferred lifecycle
        world.applyDeferredLifecycle();
    }
}
