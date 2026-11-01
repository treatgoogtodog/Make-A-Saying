package com.makeasaying.engine.entity.components;

/**
 * Entity spatial state: integer feet position, subpixel remainders, previous position for interpolation,
 * and per-tick velocity.
 */
public final class BodyComponent {
    public int x;
    public int y;
    public float subpixelX;
    public float subpixelY;

    public int prevX;
    public int prevY;

    public float vx;
    public float vy;

    public boolean grounded;
    public boolean wasGrounded;

    public long supportId = 0L; // Platform entity ID currently supporting this body (0 = none)

    public BodyComponent(int initialX, int initialY) {
        resetPosition(initialX, initialY);
    }

    public BodyComponent() {
        this(0, 0);
    }

    public void resetPosition(int newX, int newY) {
        this.x = newX;
        this.y = newY;
        this.prevX = newX;
        this.prevY = newY;
        this.subpixelX = 0f;
        this.subpixelY = 0f;
        this.vx = 0f;
        this.vy = 0f;
        this.grounded = false;
        this.wasGrounded = false;
        this.supportId = 0L;
    }
}
