package com.makeasaying.data.world;

import com.badlogic.gdx.Input;
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
import com.makeasaying.engine.input.ContextBindings;
import com.makeasaying.engine.input.InputContext;
import com.makeasaying.engine.input.InputContextStack;
import com.makeasaying.engine.input.InputManager;
import com.makeasaying.engine.input.InputState;
import com.makeasaying.engine.input.replay.InputRecorder;
import com.makeasaying.engine.input.replay.InputReplayer;
import com.makeasaying.engine.input.replay.ReplayData;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.SpawnPoint;
import com.makeasaying.engine.world.Tileset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RoomReplayStateHashTest {
    private Room room;
    private Tileset tileset;
    private TileGrid grid;
    private SpawnPoint spawn;

    private static Path resolveContentPath(String relative) {
        Path p = Path.of(relative);
        if (Files.exists(p)) return p;
        p = Path.of("..", relative);
        if (Files.exists(p)) return p;
        return Path.of(relative);
    }

    @BeforeEach
    void setUp() throws IOException {
        Path tsPath = resolveContentPath("content/tilesets/test_area.json");
        Path roomPath = resolveContentPath("content/rooms/test_01.json");

        tileset = TilesetLoader.load(tsPath);
        room = RoomLoader.load(roomPath);
        grid = RoomGridBuilder.build(room, Map.of("test_area", tileset), 16);

        spawn = room.spawnPoints().stream()
            .filter(sp -> "player_start".equals(sp.id()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("player_start spawn missing in test_01.json"));
    }

    @Test
    void testReplayDeterminismOnLoadedRoom() {
        ActionRegistry registry = new ActionRegistry();
        registry.register(ActionDefinition.button(Actions.MOVE_LEFT));
        registry.register(ActionDefinition.button(Actions.MOVE_RIGHT));
        registry.register(ActionDefinition.button(Actions.MOVE_DOWN));
        registry.register(ActionDefinition.button(Actions.JUMP));
        registry.register(ActionDefinition.axis(Actions.MOVE_X, Actions.MOVE_LEFT, Actions.MOVE_RIGHT));
        registry.register(ActionDefinition.axis(Actions.MOVE_Y, Actions.MOVE_DOWN, Actions.MOVE_UP));

        ContextBindings bindings = new ContextBindings();
        bindings.bind(InputContext.GAMEPLAY, Actions.MOVE_LEFT, Input.Keys.A);
        bindings.bind(InputContext.GAMEPLAY, Actions.MOVE_RIGHT, Input.Keys.D);
        bindings.bind(InputContext.GAMEPLAY, Actions.MOVE_DOWN, Input.Keys.S);
        bindings.bind(InputContext.GAMEPLAY, Actions.JUMP, Input.Keys.SPACE);

        InputContextStack contextStack = new InputContextStack(InputContext.GAMEPLAY);
        InputManager inputManager = new InputManager(registry, bindings, contextStack);

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

        InputRecorder recorder = new InputRecorder(60, 12345L, "room_replay_test");
        recorder.start();
        List<Long> liveStateHashes = new ArrayList<>();

        // Run 100 ticks of interactive simulation
        for (long tick = 1; tick <= 100; tick++) {
            if (tick == 2) inputManager.keyDown(Input.Keys.D); // Walk right toward slope
            if (tick == 15) inputManager.keyDown(Input.Keys.SPACE); // Jump while on slope
            if (tick == 25) inputManager.keyUp(Input.Keys.SPACE); // Variable jump cut
            if (tick == 45) inputManager.keyUp(Input.Keys.D);
            if (tick == 50) inputManager.keyDown(Input.Keys.A); // Walk back left
            if (tick == 70) inputManager.keyUp(Input.Keys.A);
            if (tick == 75) inputManager.keyDown(Input.Keys.D);
            if (tick == 80) inputManager.keyDown(Input.Keys.SPACE); // Jump toward one-way platform
            if (tick == 90) inputManager.keyUp(Input.Keys.SPACE);
            if (tick == 98) inputManager.keyUp(Input.Keys.D);

            inputManager.tick(tick, true);
            recorder.recordTick(tick, contextStack.getActiveContext(), inputManager.getState());

            controlSystem.update(world, inputManager.getState());
            movementSystem.update(world, grid);
            world.getEventQueue().dispatch();
            world.applyDeferredLifecycle();

            liveStateHashes.add(world.stateHash());
        }

        recorder.stop();
        ReplayData replayData = recorder.toReplayData();

        // Second run: Replay recorded inputs from tick 1 to 100 on loaded room
        world.reset();
        long replayPlayerId = world.createEntity();
        world.setBody(replayPlayerId, new BodyComponent(spawn.x(), spawn.y()));
        world.setCollider(replayPlayerId, new ColliderComponent(12, 36));
        world.setFacing(replayPlayerId, new FacingComponent(1));
        world.setController(replayPlayerId, new ControllerComponent(ControlSource.PLAYER));
        world.setJumpState(replayPlayerId, new JumpStateComponent());
        world.setInputState(replayPlayerId, new InputStateComponent(new InputState(registry)));
        world.applyDeferredLifecycle();

        InputReplayer replayer = new InputReplayer(replayData, registry);
        replayer.start();

        for (long tick = 1; tick <= 100; tick++) {
            replayer.tick(tick);

            controlSystem.update(world, replayer.getState());
            movementSystem.update(world, grid);
            world.getEventQueue().dispatch();
            world.applyDeferredLifecycle();

            assertEquals(liveStateHashes.get((int) tick - 1), world.stateHash(),
                "Tick " + tick + " world state hash mismatch during replay on loaded room test_01.json!");
        }
    }
}
