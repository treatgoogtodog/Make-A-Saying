package com.makeasaying.engine.entity.components;

/**
 * Moving platform component per AGENTS.md section 7.5 and M4 brief.
 * Defines AABB solid bounds, ping-pong trajectory, speed, pause, remainder stepping,
 * and squish resolution mode (BLOCK or CRUSH).
 */
public final class PlatformComponent {
    public enum CrushMode {
        BLOCK,
        CRUSH
    }

    public int width;
    public int height;

    public int startX;
    public int startY;
    public int endX;
    public int endY;

    public float speed; // in pixels per tick
    public int pauseTicks;
    public int pauseTimer;

    public int currentX;
    public int currentY;
    public int prevX;
    public int prevY;

    public float remainderX;
    public float remainderY;

    public int direction = 1; // 1 = moving toward end, -1 = moving toward start
    public CrushMode crushMode = CrushMode.BLOCK;

    public PlatformComponent(int startX, int startY, int endX, int endY, int width, int height, float speed, int pauseTicks, CrushMode crushMode) {
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.currentX = startX;
        this.currentY = startY;
        this.prevX = startX;
        this.prevY = startY;
        this.width = width;
        this.height = height;
        this.speed = speed;
        this.pauseTicks = pauseTicks;
        this.pauseTimer = 0;
        this.direction = 1;
        this.remainderX = 0f;
        this.remainderY = 0f;
        this.crushMode = crushMode != null ? crushMode : CrushMode.BLOCK;
    }

    public PlatformComponent(int startX, int startY, int endX, int endY, int width, int height, float speed) {
        this(startX, startY, endX, endY, width, height, speed, 0, CrushMode.BLOCK);
    }
}
