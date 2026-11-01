package com.makeasaying.engine.render;

/**
 * 2D camera following a target entity/point clamped to room bounds per M5a brief.
 * Snaps to whole virtual pixels at draw time.
 * If the room is smaller than the virtual view (320x180), the room is centered.
 */
public final class Camera {
    private final int viewWidth;
    private final int viewHeight;

    private float x;
    private float y;
    private float prevX;
    private float prevY;

    public Camera(int viewWidth, int viewHeight) {
        if (viewWidth <= 0 || viewHeight <= 0) {
            throw new IllegalArgumentException("Camera view dimensions must be positive");
        }
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
    }

    public void update(float targetX, float targetY, int roomTilesW, int roomTilesH, int tileSize) {
        prevX = x;
        prevY = y;

        int roomPxW = roomTilesW * tileSize;
        int roomPxH = roomTilesH * tileSize;

        float targetCamX;
        if (roomPxW <= viewWidth) {
            // Room smaller than or equal to view: center room horizontally
            targetCamX = -(viewWidth - roomPxW) / 2.0f;
        } else {
            // Follow target, clamp to room bounds [0, roomPxW - viewWidth]
            targetCamX = targetX - (viewWidth / 2.0f);
            if (targetCamX < 0) targetCamX = 0;
            if (targetCamX > roomPxW - viewWidth) targetCamX = roomPxW - viewWidth;
        }

        float targetCamY;
        if (roomPxH <= viewHeight) {
            // Room smaller than or equal to view: center room vertically
            targetCamY = -(viewHeight - roomPxH) / 2.0f;
        } else {
            // Follow target, clamp to room bounds [0, roomPxH - viewHeight]
            targetCamY = targetY - (viewHeight / 2.0f);
            if (targetCamY < 0) targetCamY = 0;
            if (targetCamY > roomPxH - viewHeight) targetCamY = roomPxH - viewHeight;
        }

        x = targetCamX;
        y = targetCamY;
    }

    public void reset(float targetX, float targetY, int roomTilesW, int roomTilesH, int tileSize) {
        update(targetX, targetY, roomTilesW, roomTilesH, tileSize);
        prevX = x;
        prevY = y;
    }

    public int getSnappedRenderX(float alpha) {
        return Snap.lerpAndSnap(prevX, x, alpha);
    }

    public int getSnappedRenderY(float alpha) {
        return Snap.lerpAndSnap(prevY, y, alpha);
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public int getViewWidth() {
        return viewWidth;
    }

    public int getViewHeight() {
        return viewHeight;
    }
}
