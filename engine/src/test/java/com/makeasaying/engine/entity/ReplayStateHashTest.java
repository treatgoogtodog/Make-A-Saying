package com.makeasaying.engine.entity;

import com.badlogic.gdx.Input;
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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReplayStateHashTest {

    @Test
    void testReplayReproducesExactWorldStateHashEveryTick() {
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
        TileGrid grid = new TileGrid(30, 20, 16);
        for (int x = 0; x < 30; x++) grid.setTile(x, 0, TileShape.SOLID);

        World world = new World();
        PlayerControlSystem controlSystem = new PlayerControlSystem();
        MovementSystem movementSystem = new MovementSystem(tuning);

        // Spawn player
        long playerId = world.createEntity();
        world.setBody(playerId, new BodyComponent(64, 16));
        world.setCollider(playerId, new ColliderComponent(12, 36));
        world.setFacing(playerId, new FacingComponent(1));
        world.setController(playerId, new ControllerComponent(ControlSource.PLAYER));
        world.setJumpState(playerId, new JumpStateComponent());
        world.setInputState(playerId, new InputStateComponent(new InputState(registry)));
        world.applyDeferredLifecycle();

        InputRecorder recorder = new InputRecorder(60, 12345L, "hash_test");
        recorder.start();

        List<Long> liveStateHashes = new ArrayList<>();

        // Live run: 40 ticks with scripted actions
        for (long tick = 1; tick <= 40; tick++) {
            if (tick == 2) inputManager.keyDown(Input.Keys.D);
            if (tick == 10) inputManager.keyDown(Input.Keys.SPACE);
            if (tick == 15) inputManager.keyUp(Input.Keys.SPACE);
            if (tick == 25) {
                inputManager.keyUp(Input.Keys.D);
                inputManager.keyDown(Input.Keys.A);
            }
            if (tick == 35) inputManager.keyUp(Input.Keys.A);

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

        // --- Replay Run ---
        world.reset();
        long replayPlayerId = world.createEntity();
        world.setBody(replayPlayerId, new BodyComponent(64, 16));
        world.setCollider(replayPlayerId, new ColliderComponent(12, 36));
        world.setFacing(replayPlayerId, new FacingComponent(1));
        world.setController(replayPlayerId, new ControllerComponent(ControlSource.PLAYER));
        world.setJumpState(replayPlayerId, new JumpStateComponent());
        world.setInputState(replayPlayerId, new InputStateComponent(new InputState(registry)));
        world.applyDeferredLifecycle();

        InputReplayer replayer = new InputReplayer(replayData, registry);
        replayer.start();

        for (long tick = 1; tick <= 40; tick++) {
            replayer.tick(tick);

            controlSystem.update(world, replayer.getState());
            movementSystem.update(world, grid);
            world.getEventQueue().dispatch();
            world.applyDeferredLifecycle();

            long replayHash = world.stateHash();
            long expectedHash = liveStateHashes.get((int) (tick - 1));

            assertEquals(expectedHash, replayHash,
                "Tick " + tick + ": Replayed World.stateHash() must match live stateHash identically");
        }
    }
}
