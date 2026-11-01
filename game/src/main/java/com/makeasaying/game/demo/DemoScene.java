package com.makeasaying.game.demo;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.makeasaying.engine.loop.Simulation;
import com.makeasaying.engine.render.PositionState;

/**
 * ============================================================================
 * TEMPORARY DEMO SCENE - M1 ONLY
 * Deletion point: To be replaced entirely in M3 with Entities/Movement systems.
 * ============================================================================
 * Hard-coded 20x12 tile room with procedural tile textures and one moving test box.
 * Box moves a fixed number of pixels per tick (never wall-clock time) and renders
 * using sub-tick position interpolation and whole-pixel snapping.
 */
public final class DemoScene implements Simulation, Disposable {
    public static final int ROOM_TILES_X = 20;
    public static final int ROOM_TILES_Y = 12;
    public static final int TILE_SIZE = 16;
    public static final float BOX_SPEED_PX_PER_TICK = 1.0f; // 1 pixel per simulation tick

    private final PositionState boxPosition = new PositionState(16f, 32f);
    private float boxDirection = 1.0f;

    private Texture floorTileTexture;
    private Texture wallTileTexture;
    private Texture boxTexture;

    public DemoScene() {
        generateProceduralTextures();
    }

    private void generateProceduralTextures() {
        // Floor tile: 16x16 subtle dark slate pattern
        Pixmap floorPixmap = new Pixmap(TILE_SIZE, TILE_SIZE, Pixmap.Format.RGBA8888);
        floorPixmap.setColor(Color.valueOf("#22232a"));
        floorPixmap.fill();
        floorPixmap.setColor(Color.valueOf("#2c2d38"));
        floorPixmap.drawRectangle(0, 0, TILE_SIZE, TILE_SIZE);
        floorTileTexture = new Texture(floorPixmap);
        floorPixmap.dispose();

        // Wall tile: 16x16 bordered brick
        Pixmap wallPixmap = new Pixmap(TILE_SIZE, TILE_SIZE, Pixmap.Format.RGBA8888);
        wallPixmap.setColor(Color.valueOf("#3a3f58"));
        wallPixmap.fill();
        wallPixmap.setColor(Color.valueOf("#565d7e"));
        wallPixmap.drawRectangle(0, 0, TILE_SIZE, TILE_SIZE);
        wallTileTexture = new Texture(wallPixmap);
        wallPixmap.dispose();

        // Moving test box: 16x16 bright teal box
        Pixmap boxPixmap = new Pixmap(TILE_SIZE, TILE_SIZE, Pixmap.Format.RGBA8888);
        boxPixmap.setColor(Color.valueOf("#38b764"));
        boxPixmap.fill();
        boxPixmap.setColor(Color.valueOf("#a7f070"));
        boxPixmap.drawRectangle(0, 0, TILE_SIZE, TILE_SIZE);
        boxTexture = new Texture(boxPixmap);
        boxPixmap.dispose();
    }

    @Override
    public void tick(long currentTick) {
        // Strict deterministic simulation: advances by fixed pixels per tick only!
        float currentX = boxPosition.getCurrX();
        float currentY = boxPosition.getCurrY();

        float minX = 16f;
        float maxX = (ROOM_TILES_X - 2) * TILE_SIZE; // inside wall bounds

        if (currentX + boxDirection * BOX_SPEED_PX_PER_TICK > maxX) {
            boxDirection = -1.0f;
        } else if (currentX + boxDirection * BOX_SPEED_PX_PER_TICK < minX) {
            boxDirection = 1.0f;
        }

        float nextX = currentX + boxDirection * BOX_SPEED_PX_PER_TICK;
        boxPosition.step(nextX, currentY);
    }

    public void render(SpriteBatch batch, float alpha) {
        // 1. Render tile room (20x12)
        for (int ty = 0; ty < ROOM_TILES_Y; ty++) {
            for (int tx = 0; tx < ROOM_TILES_X; tx++) {
                boolean isBorder = (tx == 0 || tx == ROOM_TILES_X - 1 || ty == 0 || ty == ROOM_TILES_Y - 1);
                Texture tile = isBorder ? wallTileTexture : floorTileTexture;
                batch.draw(tile, tx * TILE_SIZE, ty * TILE_SIZE);
            }
        }

        // 2. Render moving test box with sub-tick interpolation and pixel snap
        int snappedX = boxPosition.getSnappedX(alpha);
        int snappedY = boxPosition.getSnappedY(alpha);
        batch.draw(boxTexture, snappedX, snappedY);
    }

    public PositionState getBoxPosition() {
        return boxPosition;
    }

    @Override
    public void dispose() {
        if (floorTileTexture != null) floorTileTexture.dispose();
        if (wallTileTexture != null) wallTileTexture.dispose();
        if (boxTexture != null) boxTexture.dispose();
    }
}
