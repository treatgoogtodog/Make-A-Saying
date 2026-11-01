package com.makeasaying.engine.entity.systems;

import com.makeasaying.engine.entity.ControlSource;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.components.ControllerComponent;
import com.makeasaying.engine.entity.components.InputStateComponent;
import com.makeasaying.engine.input.InputState;

/**
 * System 1 in M3: Copies active player input into the controlled entity's InputStateComponent.
 * Systems hold no private state.
 */
public final class PlayerControlSystem {
    public PlayerControlSystem() {}

    public void update(World world, InputState activePlayerInput) {
        if (activePlayerInput == null) return;

        for (int i = 1; i < World.MAX_ENTITIES; i++) {
            if (!world.isAliveIndex(i)) continue;

            ControllerComponent controller = world.getControllerByIndex(i);
            InputStateComponent inputComponent = world.getInputStateByIndex(i);

            if (controller != null && controller.source == ControlSource.PLAYER && inputComponent != null) {
                inputComponent.state.copyFrom(activePlayerInput);
            }
        }
    }
}
