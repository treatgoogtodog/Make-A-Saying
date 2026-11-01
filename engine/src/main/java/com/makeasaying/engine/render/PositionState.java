package com.makeasaying.engine.render;

/**
 * Tracks previous and current position for tick-based motion and sub-tick interpolation.
 * Calling reset() prevents smearing when teleporting or spawning.
 */
public final class PositionState {
    private float prevX;
    private float prevY;
    private float currX;
    private float currY;

    public PositionState(float initialX, float initialY) {
        reset(initialX, initialY);
    }

    public PositionState() {
        this(0f, 0f);
    }

    /**
     * Resets both previous and current positions to the given coordinates,
     * ensuring immediate teleportation without interpolation smearing.
     */
    public void reset(float x, float y) {
        this.prevX = x;
        this.prevY = y;
        this.currX = x;
        this.currY = y;
    }

    /**
     * Steps the state forward on a simulation tick, archiving the current position as previous.
     */
    public void step(float newX, float newY) {
        this.prevX = this.currX;
        this.prevY = this.currY;
        this.currX = newX;
        this.currY = newY;
    }

    public float getPrevX() {
        return prevX;
    }

    public float getPrevY() {
        return prevY;
    }

    public float getCurrX() {
        return currX;
    }

    public float getCurrY() {
        return currY;
    }

    public int getSnappedX(float alpha) {
        return Snap.lerpAndSnap(prevX, currX, alpha);
    }

    public int getSnappedY(float alpha) {
        return Snap.lerpAndSnap(prevY, currY, alpha);
    }
}
