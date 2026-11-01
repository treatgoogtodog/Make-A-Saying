package com.makeasaying.engine.entity;

import com.badlogic.gdx.Input;
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
import com.makeasaying.engine.input.ContextBindings;
import com.makeasaying.engine.input.InputContext;
import com.makeasaying.engine.input.InputContextStack;
import com.makeasaying.engine.input.InputManager;
import com.makeasaying.engine.input.InputState;
import com.makeasaying.engine.input.replay.InputRecorder;
import com.makeasaying.engine.input.replay.InputReplayer;
import com.makeasaying.engine.input.replay.ReplayData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformReplayStateHashTest {

    @Test
    void testReplayReproducesExactWorldStateHashWithSlopesAndPlatforms() {
        ActionRegistry registry = new ActionRegistry();
        registry.register(ActionDefinition.button(Actions.MOVE_LEFT));
        registry.register(ActionDefinition.button(Actions.MOVE_RIGHT));
        registry.register(ActionDefinition.button(Actions.JUMP));
        registry.register(ActionDefinition.axis(Actions.MOVE_X, Actions.MOVE_LEFT, Actions.MOVE_RIGHT));

        ContextBindings bindings = new ContextBindings();
        bindings.bind(InputContext.GAMEPLAY, Actions.MOVE_LEFT, Input.Keys.A);
        bindings.bind(InputContext.GAMEPLAY, Actions.MOVE_RIGHT, Input.Keys.D);
        bindings.bind(InputContext.GAMEPLAY, Actions.JUMP, Input.Keys.SPACE);

        InputContextStack contextStack = new InputContextStack(InputContext.GAMEPLAY);
        InputManager inputManager = new InputManager(registry, bindings, contextStack);

        MovementTuning tuning = MovementTuning.defaultTuning(60);
        TileGrid grid = new TileGrid(40, 20, 16);
        for (int x = 0; x < 40; x++) grid.setTile(x, 0, TileShape.SOLID);
        grid.setTile(5, 1, TileShape.SLOPE_45_UP_RIGHT);
        grid.setTile(6, 1, TileShape.SLOPE_45_UP_RIGHT);
        grid.setTile(7, 2, TileShape.SOLID);
        grid.setTile(12, 3, TileShape.ONE_WAY);

        World world = new World();
        PlayerControlSystem controlSystem = new PlayerControlSystem();
        PlatformSystem platformSystem = new PlatformSystem();
        MovementSystem movementSystem = new MovementSystem(tuning);

        // Player entity
        long playerId = world.createEntity();
        world.setBody(playerId, new BodyComponent(32, 16));
        world.setCollider(playerId, new ColliderComponent(12, 36));
        world.setFacing(playerId, new FacingComponent(1));
        world.setController(playerId, new ControllerComponent(ControlSource.PLAYER));
        world.setJumpState(playerId, new JumpStateComponent());
        world.setInputState(playerId, new InputStateComponent(new InputState(registry)));

        // Moving platform entity
        long platId = world.createEntity();
        PlatformComponent plat = new PlatformComponent(160, 32, 240, 32, 48, 16, 2.0f);
        world.setPlatform(platId, plat);
        world.applyDeferredLifecycle();

        InputRecorder recorder = new InputRecorder(60, 12345L, "platform_replay_test");
        recorder.start();

        int totalTicks = 80;
        List<Long> recordedHashes = new ArrayList<>();

        // Pass 1: Live recorded run
        for (long tick = 1; tick <= totalTicks; tick++) {
            if (tick == 5) inputManager.keyDown(Input.Keys.D); // Run right up slope
            if (tick == 20) inputManager.keyDown(Input.Keys.SPACE); // Jump on slope
            if (tick == 28) inputManager.keyUp(Input.Keys.SPACE);
            if (tick == 45) inputManager.keyUp(Input.Keys.D);
            if (tick == 55) inputManager.keyDown(Input.Keys.A); // Run left down slope
            if (tick == 70) inputManager.keyUp(Input.Keys.A);

            inputManager.tick(tick, true);
            recorder.recordTick(tick, contextStack.getActiveContext(), inputManager.getState());

            controlSystem.update(world, inputManager.getState());
            platformSystem.update(world, grid);
            movementSystem.update(world, grid);
            world.getEventQueue().dispatch();
            world.applyDeferredLifecycle();

            recordedHashes.add(world.stateHash());
        }

        recorder.stop();
        ReplayData replay = recorder.toReplayData();

        // Pass 2: Replay run
        world.reset();
        long p2 = world.createEntity();
        world.setBody(p2, new BodyComponent(32, 16));
        world.setCollider(p2, new ColliderComponent(12, 36));
        world.setFacing(p2, new FacingComponent(1));
        world.setController(p2, new ControllerComponent(ControlSource.PLAYER));
        world.setJumpState(p2, new JumpStateComponent());
        world.setInputState(p2, new InputStateComponent(new InputState(registry)));

        long plat2 = world.createEntity();
        world.setPlatform(plat2, new PlatformComponent(160, 32, 240, 32, 48, 16, 2.0f));
        world.applyDeferredLifecycle();

        InputReplayer replayer = new InputReplayer(replay, registry);
        replayer.start();

        for (long tick = 1; tick <= totalTicks; tick++) {
            replayer.tick(tick);

            controlSystem.update(world, replayer.getState());
            platformSystem.update(world, grid);
            movementSystem.update(world, grid);
            world.getEventQueue().dispatch();
            world.applyDeferredLifecycle();

            long replayHash = world.stateHash();
            assertEquals(recordedHashes.get((int) (tick - 1)), replayHash,
                "Replay world state hash mismatch at tick " + tick);
        }
    }
}
