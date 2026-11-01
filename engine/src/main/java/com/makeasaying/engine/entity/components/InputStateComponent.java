package com.makeasaying.engine.entity.components;

import com.makeasaying.engine.input.InputState;

/**
 * Entity component holding per-entity action states and movement axes.
 */
public final class InputStateComponent {
    public final InputState state;

    public InputStateComponent(InputState state) {
        this.state = state;
    }
}
