package com.makeasaying.engine.entity.components;

import com.makeasaying.engine.entity.ControlSource;

/**
 * Entity controller ownership and puppet flag per AGENTS.md section 7.2.
 */
public final class ControllerComponent {
    public ControlSource source = ControlSource.NONE;
    public boolean puppet = false;

    public ControllerComponent(ControlSource source) {
        this.source = source != null ? source : ControlSource.NONE;
    }

    public ControllerComponent() {
        this(ControlSource.NONE);
    }
}
