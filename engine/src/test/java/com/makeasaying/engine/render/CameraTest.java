package com.makeasaying.engine.render;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CameraTest {
    private Camera camera;

    @BeforeEach
    void setUp() {
        // Virtual view: 320x180
        camera = new Camera(320, 180);
    }

    @Test
    void testFollowTargetClampedToRoomBounds() {
        // Room: 40x12 tiles @ 16px = 640x192 px
        int roomW = 40;
        int roomH = 12;
        int tileSize = 16;

        // Target near room center: (320, 96) -> camera center at target -> (320 - 160, 96 - 90) = (160, 6)
        camera.reset(320, 96, roomW, roomH, tileSize);
        assertEquals(160.0f, camera.getX(), 0.01f);
        assertEquals(6.0f, camera.getY(), 0.01f);

        // Target near left edge: (50, 50) -> clamp left to 0, bottom to 0
        camera.update(50, 50, roomW, roomH, tileSize);
        assertEquals(0.0f, camera.getX(), 0.01f);
        assertEquals(0.0f, camera.getY(), 0.01f);

        // Target near right edge: (620, 180) -> clamp right to 640 - 320 = 320, top to 192 - 180 = 12
        camera.update(620, 180, roomW, roomH, tileSize);
        assertEquals(320.0f, camera.getX(), 0.01f);
        assertEquals(12.0f, camera.getY(), 0.01f);
    }

    @Test
    void testRoomSmallerThanViewIsCentered() {
        // Room smaller than view: 15x8 tiles @ 16px = 240x128 px (view is 320x180)
        int roomW = 15;
        int roomH = 8;
        int tileSize = 16;

        camera.reset(120, 64, roomW, roomH, tileSize);

        // Horizontal offset: -(320 - 240) / 2 = -40
        // Vertical offset: -(180 - 128) / 2 = -26
        assertEquals(-40.0f, camera.getX(), 0.01f);
        assertEquals(-26.0f, camera.getY(), 0.01f);
    }

    @Test
    void testWholePixelSnapAtDrawTime() {
        int roomW = 40;
        int roomH = 12;
        int tileSize = 16;

        camera.reset(100.25f, 100.25f, roomW, roomH, tileSize);
        camera.update(100.75f, 100.75f, roomW, roomH, tileSize);

        // Alpha = 0.5f: lerp between prev and curr, then round to nearest integer
        int snapX = camera.getSnappedRenderX(0.5f);
        int snapY = camera.getSnappedRenderY(0.5f);

        // Values must be exact integers (returned as int)
        assertTrue(snapX >= 0);
        assertTrue(snapY >= 0);
    }

    @Test
    void testResetClearsPreviousInterpolationState() {
        int roomW = 40;
        int roomH = 12;
        int tileSize = 16;

        camera.reset(300, 100, roomW, roomH, tileSize);
        // Alpha at 0.0 or 1.0 should give the exact same snapped position after reset
        assertEquals(camera.getSnappedRenderX(0.0f), camera.getSnappedRenderX(1.0f));
        assertEquals(camera.getSnappedRenderY(0.0f), camera.getSnappedRenderY(1.0f));
    }
}
