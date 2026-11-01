package com.makeasaying.engine.entity.components;

/**
 * Tracks jump timers and variable jump release state in integer ticks.
 */
public final class JumpStateComponent {
    public int coyoteTimer = 0;
    public int jumpBufferTimer = 0;
    public boolean hasJumpCut = false;
    public boolean hasAirJumped = false;

    public void reset() {
        coyoteTimer = 0;
        jumpBufferTimer = 0;
        hasJumpCut = false;
        hasAirJumped = false;
    }
}
