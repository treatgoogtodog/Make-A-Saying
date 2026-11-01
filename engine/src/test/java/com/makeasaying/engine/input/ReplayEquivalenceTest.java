package com.makeasaying.engine.input;

import com.badlogic.gdx.Input;
import com.makeasaying.engine.input.replay.InputRecorder;
import com.makeasaying.engine.input.replay.InputReplayer;
import com.makeasaying.engine.input.replay.ReplayData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReplayEquivalenceTest {

    record StateSnapshot(
        long tick,
        boolean jumpDown,
        boolean jumpPressed,
        boolean jumpReleased,
        int moveXAxis,
        boolean interactDown
    ) {}

    @Test
    void testRecordAndReplayScriptedSequenceWithSubTickTapAndContextChange() {
        ActionRegistry registry = new ActionRegistry();
        registry.register(ActionDefinition.button("move_left"));
        registry.register(ActionDefinition.button("move_right"));
        registry.register(ActionDefinition.button("jump"));
        registry.register(ActionDefinition.button("interact"));
        registry.register(ActionDefinition.button("cancel", true));
        registry.register(ActionDefinition.button("pause", true));
        registry.register(ActionDefinition.axis("move_x", "move_left", "move_right"));

        ContextBindings bindings = new ContextBindings();
        bindings.bind(InputContext.GAMEPLAY, "move_left", Input.Keys.A);
        bindings.bind(InputContext.GAMEPLAY, "move_right", Input.Keys.D);
        bindings.bind(InputContext.GAMEPLAY, "jump", Input.Keys.SPACE);
        bindings.bind(InputContext.GAMEPLAY, "cancel", Input.Keys.ESCAPE);

        bindings.bind(InputContext.MENU, "interact", Input.Keys.SPACE);
        bindings.bind(InputContext.MENU, "cancel", Input.Keys.ESCAPE);

        bindings.bind(InputContext.GLOBAL, "pause", Input.Keys.ESCAPE);

        InputContextStack contextStack = new InputContextStack(InputContext.GAMEPLAY);
        InputManager manager = new InputManager(registry, bindings, contextStack);

        InputRecorder recorder = new InputRecorder(60, 100L, "test_hash");
        recorder.start();

        List<StateSnapshot> liveSnapshots = new ArrayList<>();

        // Run 20 ticks of scripted events
        for (long tick = 1; tick <= 20; tick++) {
            // Scripted event injections
            if (tick == 2) {
                // Sub-tick tap: press and release in same tick
                manager.keyDown(Input.Keys.SPACE);
                manager.keyUp(Input.Keys.SPACE);
            } else if (tick == 5) {
                // Hold move right
                manager.keyDown(Input.Keys.D);
            } else if (tick == 8) {
                // Context switch to MENU while holding D
                contextStack.push(InputContext.MENU);
            } else if (tick == 12) {
                // Press interact (SPACE in MENU)
                manager.keyDown(Input.Keys.SPACE);
            } else if (tick == 15) {
                // Context switch back to GAMEPLAY
                contextStack.pop();
            } else if (tick == 16) {
                // Release SPACE
                manager.keyUp(Input.Keys.SPACE);
            }

            manager.tick(tick, true);
            InputState state = manager.getState();

            // Record tick
            recorder.recordTick(tick, contextStack.getActiveContext(), state);

            // Archive live snapshot
            liveSnapshots.add(new StateSnapshot(
                tick,
                state.isDown("jump"),
                state.isPressed("jump"),
                state.isReleased("jump"),
                state.getAxis("move_x"),
                state.isDown("interact")
            ));
        }

        recorder.stop();
        ReplayData replayData = recorder.toReplayData();
        assertNotNull(replayData);
        assertTrue(replayData.frames().size() > 0);

        // --- Replay Verification ---
        InputReplayer replayer = new InputReplayer(replayData, registry);
        replayer.start();

        List<StateSnapshot> replayedSnapshots = new ArrayList<>();
        for (long tick = 1; tick <= 20; tick++) {
            replayer.tick(tick);
            InputState repState = replayer.getState();

            replayedSnapshots.add(new StateSnapshot(
                tick,
                repState.isDown("jump"),
                repState.isPressed("jump"),
                repState.isReleased("jump"),
                repState.getAxis("move_x"),
                repState.isDown("interact")
            ));
        }

        // Assert exact tick-by-tick equivalence
        assertEquals(liveSnapshots.size(), replayedSnapshots.size());
        for (int i = 0; i < liveSnapshots.size(); i++) {
            StateSnapshot live = liveSnapshots.get(i);
            StateSnapshot rep = replayedSnapshots.get(i);
            assertEquals(live.jumpDown(), rep.jumpDown(), "Tick " + live.tick() + ": jumpDown must match");
            assertEquals(live.jumpPressed(), rep.jumpPressed(), "Tick " + live.tick() + ": jumpPressed must match");
            assertEquals(live.jumpReleased(), rep.jumpReleased(), "Tick " + live.tick() + ": jumpReleased must match");
            assertEquals(live.moveXAxis(), rep.moveXAxis(), "Tick " + live.tick() + ": moveXAxis must match");
            assertEquals(live.interactDown(), rep.interactDown(), "Tick " + live.tick() + ": interactDown must match");
        }
    }
}
