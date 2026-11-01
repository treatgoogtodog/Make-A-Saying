package com.makeasaying.data.world;

import com.makeasaying.engine.entity.ControlSource;
import com.makeasaying.engine.entity.MovementTuning;
import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.engine.entity.World;
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
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.SpawnPoint;
import com.makeasaying.engine.world.Tileset;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class M5aAllocationTest {

    private static Path resolveContentPath(String relative) {
        Path p = Path.of(relative);
        if (Files.exists(p)) return p;
        p = Path.of("..", relative);
        if (Files.exists(p)) return p;
        return Path.of(relative);
    }

    @Test
    void testZeroAllocationsOnLoadedRoomSimulationLoop() throws Exception {
        java.lang.management.ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (!(threadBean instanceof com.sun.management.ThreadMXBean sunThreadBean)) {
            return;
        }
        if (!sunThreadBean.isThreadAllocatedMemorySupported()) {
            return;
        }

        Path tsPath = resolveContentPath("content/tilesets/test_area.json");
        Path roomPath = resolveContentPath("content/rooms/test_01.json");

        Tileset tileset = TilesetLoader.load(tsPath);
        Room room = RoomLoader.load(roomPath);
        TileGrid grid = RoomGridBuilder.build(room, Map.of("test_area", tileset), 16);

        SpawnPoint spawn = room.spawnPoints().stream()
            .filter(sp -> "player_start".equals(sp.id()))
            .findFirst()
            .orElseThrow();

        ActionRegistry registry = new ActionRegistry();
        registry.register(ActionDefinition.button(Actions.MOVE_LEFT));
        registry.register(ActionDefinition.button(Actions.MOVE_RIGHT));
        registry.register(ActionDefinition.button(Actions.MOVE_DOWN));
        registry.register(ActionDefinition.button(Actions.JUMP));
        registry.register(ActionDefinition.axis(Actions.MOVE_X, Actions.MOVE_LEFT, Actions.MOVE_RIGHT));
        registry.register(ActionDefinition.axis(Actions.MOVE_Y, Actions.MOVE_DOWN, Actions.MOVE_UP));

        MovementTuning tuning = MovementTuning.defaultTuning(60);
        World world = new World();
        PlayerControlSystem controlSystem = new PlayerControlSystem();
        MovementSystem movementSystem = new MovementSystem(tuning);

        long playerId = world.createEntity();
        world.setBody(playerId, new BodyComponent(spawn.x(), spawn.y()));
        world.setCollider(playerId, new ColliderComponent(12, 36));
        world.setFacing(playerId, new FacingComponent(1));
        world.setController(playerId, new ControllerComponent(ControlSource.PLAYER));
        world.setJumpState(playerId, new JumpStateComponent());
        world.setInputState(playerId, new InputStateComponent(new InputState(registry)));

        InputState state = world.getInputState(playerId).state;

        // Warm up JIT to avoid classloading or tiered compilation allocations
        for (int i = 0; i < 5000; i++) {
            state.beginTick();
            state.setButtonDown(Actions.MOVE_RIGHT, i % 60 < 30);
            state.setButtonDown(Actions.JUMP, i % 120 == 0);
            controlSystem.update(world, state);
            movementSystem.update(world, grid);
            world.getEventQueue().dispatch();
            world.applyDeferredLifecycle();
        }

        long threadId = Thread.currentThread().threadId();
        long memBefore = sunThreadBean.getThreadAllocatedBytes(threadId);

        int iterations = 100000;
        for (int i = 0; i < iterations; i++) {
            state.beginTick();
            state.setButtonDown(Actions.MOVE_RIGHT, i % 60 < 30);
            state.setButtonDown(Actions.JUMP, i % 120 == 0);
            controlSystem.update(world, state);
            movementSystem.update(world, grid);
            world.getEventQueue().dispatch();
            world.applyDeferredLifecycle();
        }

        long memAfter = sunThreadBean.getThreadAllocatedBytes(threadId);
        long allocated = memAfter - memBefore;

        assertTrue(allocated < 1024,
            "Loaded room simulation tick loop must allocate near 0 bytes, but allocated: " + allocated + " bytes across " + iterations + " iterations");
    }
}
