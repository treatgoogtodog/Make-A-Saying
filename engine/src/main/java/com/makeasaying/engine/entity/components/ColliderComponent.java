package com.makeasaying.engine.entity.components;

/**
 * Axis-aligned bounding box collider anchored at the entity feet per AGENTS.md section 6.
 * Dimensions are dynamic and stored in component data, not hard-coded in physics systems.
 */
public final class ColliderComponent {
    public int width;
    public int height;
    public int offsetX;
    public int offsetY;

    public ColliderComponent(int width, int height) {
        this.width = width;
        this.height = height;
        this.offsetX = -width / 2; // Bottom-center feet anchor
        this.offsetY = 0;
    }

    public ColliderComponent() {
        this(12, 36); // Default 12x36 protagonist collider
    }

    public int getLeft(int feetX) {
        return feetX + offsetX;
    }

    public int getRight(int feetX) {
        return feetX + offsetX + width;
    }

    public int getBottom(int feetY) {
        return feetY + offsetY;
    }

    public int getTop(int feetY) {
        return feetY + offsetY + height;
    }
}
