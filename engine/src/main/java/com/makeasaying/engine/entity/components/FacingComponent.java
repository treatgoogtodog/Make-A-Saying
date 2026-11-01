package com.makeasaying.engine.entity.components;

/**
 * Entity horizontal facing direction (-1 for left, +1 for right).
 */
public final class FacingComponent {
    public int direction = 1;

    public FacingComponent(int initialDirection) {
        this.direction = (initialDirection < 0) ? -1 : 1;
    }

    public FacingComponent() {
        this(1);
    }
}
