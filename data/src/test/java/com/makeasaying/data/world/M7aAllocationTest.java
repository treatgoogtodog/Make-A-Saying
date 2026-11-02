package com.makeasaying.data.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeasaying.data.condition.ConditionLoader;
import com.makeasaying.engine.condition.AtomRegistry;
import com.makeasaying.engine.condition.CompiledCondition;
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
import com.makeasaying.engine.entity.events.EntityEventQueue;
import com.makeasaying.engine.entity.systems.MovementSystem;
import com.makeasaying.engine.entity.systems.PlatformSystem;
import com.makeasaying.engine.entity.systems.PlayerControlSystem;
import com.makeasaying.engine.flags.FlagChangeLogEntry;
import com.makeasaying.engine.flags.FlagDefinition;
import com.makeasaying.engine.flags.FlagRegistry;
import com.makeasaying.engine.flags.FlagScope;
import com.makeasaying.engine.flags.FlagStore;
import com.makeasaying.engine.flags.FlagType;
import com.makeasaying.engine.input.InputState;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomExit;
import com.makeasaying.engine.world.RoomStateStore;
import com.makeasaying.engine.world.SpawnPoint;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class M7aAllocationTest {
    private static final int ITERATIONS = 100000;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testZeroAllocationDuringSimulationWithFlagsAndConditions() throws Exception {
        java.lang.management.ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (!(threadBean instanceof com.sun.management.ThreadMXBean sunThreadBean) ||
            !sunThreadBean.isThreadAllocatedMemorySupported()) {
            return;
        }

        com.makeasaying.engine.input.ActionRegistry actionRegistry = new com.makeasaying.engine.input.ActionRegistry();
        actionRegistry.register(com.makeasaying.engine.input.ActionDefinition.button(com.makeasaying.engine.input.Actions.MOVE_LEFT));
        actionRegistry.register(com.makeasaying.engine.input.ActionDefinition.button(com.makeasaying.engine.input.Actions.MOVE_RIGHT));
        actionRegistry.register(com.makeasaying.engine.input.ActionDefinition.button(com.makeasaying.engine.input.Actions.MOVE_DOWN));
        actionRegistry.register(com.makeasaying.engine.input.ActionDefinition.button(com.makeasaying.engine.input.Actions.JUMP));
        actionRegistry.register(com.makeasaying.engine.input.ActionDefinition.axis(com.makeasaying.engine.input.Actions.MOVE_X, com.makeasaying.engine.input.Actions.MOVE_LEFT, com.makeasaying.engine.input.Actions.MOVE_RIGHT));
        actionRegistry.register(com.makeasaying.engine.input.ActionDefinition.axis(com.makeasaying.engine.input.Actions.MOVE_Y, com.makeasaying.engine.input.Actions.MOVE_DOWN, com.makeasaying.engine.input.Actions.MOVE_UP));

        World world = new World();
        long playerId = world.createEntity();
        world.setBody(playerId, new BodyComponent(32, 16));
        world.setCollider(playerId, new ColliderComponent(12, 36));
        world.setInputState(playerId, new InputStateComponent(new InputState(actionRegistry)));
        world.setJumpState(playerId, new JumpStateComponent());
        world.setController(playerId, new ControllerComponent(com.makeasaying.engine.entity.ControlSource.PLAYER));
        world.setRoomScope(playerId, new RoomScopeComponent(RoomScopeComponent.Scope.PERSISTENT_CARRIED));

        long platformId = world.createEntity();
        world.setBody(platformId, new BodyComponent(64, 48));
        world.setCollider(platformId, new ColliderComponent(32, 8));
        world.setPlatform(platformId, new PlatformComponent(64, 48, 128, 48, 32, 8, 60.0f, 15, PlatformComponent.CrushMode.BLOCK));

        world.applyDeferredLifecycle();

        TileGrid grid = new TileGrid(40, 12, 16);
        for (int x = 0; x < 40; x++) {
            grid.setTile(x, 0, TileShape.SOLID);
        }

        FlagDefinition fStage = new FlagDefinition(0, "test.stage", FlagType.INT, FlagScope.GLOBAL, 0, 0, 1000000, null, "doc");
        FlagDefinition fDoor = new FlagDefinition(1, "test.door_open", FlagType.BOOL, FlagScope.ROOM, 0, 0, 1, null, "doc");
        FlagRegistry registry = new FlagRegistry(List.of(fStage, fDoor));
        AtomRegistry atomRegistry = new AtomRegistry();
        RoomStateStore roomStateStore = new RoomStateStore();
        FlagStore flagStore = new FlagStore(registry, roomStateStore);

        CompiledCondition cond = ConditionLoader.parse(
            MAPPER.readTree("{\"all\": [{\"flag\": \"test.stage\", \"op\": \">=\", \"value\": 0}, {\"flag\": \"test.door_open\", \"op\": \"==\", \"value\": false}]}"),
            registry, atomRegistry
        );

        PlayerControlSystem controlSystem = new PlayerControlSystem();
        PlatformSystem platformSystem = new PlatformSystem();
        MovementSystem movementSystem = new MovementSystem(MovementTuning.defaultTuning(60));
        InputState input = new InputState(actionRegistry);
        EntityEventQueue eventQueue = world.getEventQueue();

        long threadId = Thread.currentThread().threadId();
        sunThreadBean.getThreadAllocatedBytes(threadId); // Prime

        // Warm up with 25k ticks
        for (int i = 0; i < 25000; i++) {
            runSimulationTick(world, playerId, controlSystem, platformSystem, movementSystem, grid, input, flagStore, cond, eventQueue, i);
        }

        // Window 1: 100k ticks
        long memBefore1 = sunThreadBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < ITERATIONS; i++) {
            runSimulationTick(world, playerId, controlSystem, platformSystem, movementSystem, grid, input, flagStore, cond, eventQueue, 25000 + i);
        }
        long memAfter1 = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocated1 = memAfter1 - memBefore1;

        // Window 2: 100k ticks
        long memBefore2 = sunThreadBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < ITERATIONS; i++) {
            runSimulationTick(world, playerId, controlSystem, platformSystem, movementSystem, grid, input, flagStore, cond, eventQueue, 125000 + i);
        }
        long memAfter2 = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocated2 = memAfter2 - memBefore2;

        System.out.println("M7a Simulation ticks window 1 (100k): " + allocated1 + " bytes, window 2 (100k): " + allocated2 + " bytes");

        assertEquals(0, allocated1, "M7a sim ticks window 1 must be strictly 0 bytes (was " + allocated1 + " bytes)");
        assertEquals(0, allocated2, "M7a sim ticks window 2 must be strictly 0 bytes (was " + allocated2 + " bytes)");
    }

    private void runSimulationTick(
        World world,
        long playerId,
        PlayerControlSystem controlSystem,
        PlatformSystem platformSystem,
        MovementSystem movementSystem,
        TileGrid grid,
        InputState input,
        FlagStore flagStore,
        CompiledCondition cond,
        EntityEventQueue eventQueue,
        long tick
    ) {
        // Enqueue a flag mutation
        flagStore.add(0, 1, FlagChangeLogEntry.SOURCE_GAMEPLAY);

        // System 2: Input
        controlSystem.update(world, input);

        // System 4: Moving platforms
        platformSystem.update(world, grid);

        // System 5: Movement
        movementSystem.update(world, grid);

        // Step 8: Apply flags & evaluate condition
        flagStore.applyPendingChanges("room_01", eventQueue, tick);
        boolean ok = cond.evaluate(flagStore, "room_01");

        // Step 9: Event dispatch
        eventQueue.dispatch();

        // Step 11: Lifecycle
        world.applyDeferredLifecycle();
    }
}
