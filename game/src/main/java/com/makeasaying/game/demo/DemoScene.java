package com.makeasaying.game.demo;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.makeasaying.engine.input.Actions;
import com.makeasaying.engine.input.InputState;
import com.makeasaying.engine.render.PositionState;

/**
 * ============================================================================
 * TEMPORARY DEMO SCENE - M1/M2 ONLY
 * Deletion point: To be replaced entirely in M3 with Entities/Movement systems.
 * ============================================================================
 * 20x12 tile room with a test box driven by digital move_x/move_y actions.
 * Box changes color while 'jump' is down.
 */
public final class DemoScene implements Disposable {
    public static final int ROOM_TILES_X = 20;
    public static final int ROOM_TILES_Y = 12;
    public static final int TILE_SIZE = 16;
    public static final float BOX_SPEED_PX_PER_TICK = 2.0f; // 2 pixels per simulation tick

    private final PositionState boxPosition = new PositionState(32f, 32f);
    private boolean isJumping = false;

    private Texture floorTileTexture;
    private Texture wallTileTexture;
    private Texture boxNormalTexture;
    private Texture boxJumpTexture;

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

        // Box normal texture: teal/green
        Pixmap normalPixmap = new Pixmap(TILE_SIZE, TILE_SIZE, Pixmap.Format.RGBA8888);
        normalPixmap.setColor(Color.valueOf("#38b764"));
        normalPixmap.fill();
        normalPixmap.setColor(Color.valueOf("#a7f070"));
        normalPixmap.drawRectangle(0, 0, TILE_SIZE, TILE_SIZE);
        boxNormalTexture = new Texture(normalPixmap);
        normalPixmap.dispose();

        // Box jump texture: golden yellow
        Pixmap jumpPixmap = new Pixmap(TILE_SIZE, TILE_SIZE, Pixmap.Format.RGBA8888);
        jumpPixmap.setColor(Color.valueOf("#f4b41b"));
        jumpPixmap.fill();
        jumpPixmap.setColor(Color.valueOf("#ffe74c"));
        jumpPixmap.drawRectangle(0, 0, TILE_SIZE, TILE_SIZE);
        boxJumpTexture = new Texture(jumpPixmap);
        jumpPixmap.dispose();
    }

    public void tick(long currentTick, InputState inputState) {
        int moveX = inputState.getAxis(Actions.MOVE_X);
        int moveY = inputState.getAxis(Actions.MOVE_Y);
        this.isJumping = inputState.isDown(Actions.JUMP);

        float minX = 16f;
        float maxX = (ROOM_TILES_X - 2) * TILE_SIZE;
        float minY = 16f;
        float maxY = (ROOM_TILES_Y - 2) * TILE_SIZE;

        float nextX = Math.max(minX, Math.min(maxX, boxPosition.getCurrX() + moveX * BOX_SPEED_PX_PER_TICK));
        float nextY = Math.max(minY, Math.min(maxY, boxPosition.getCurrY() + moveY * BOX_SPEED_PX_PER_TICK));

        boxPosition.step(nextX, nextY);
    }

    public void render(SpriteBatch batch, float alpha) {
        // 1. Render tile room
        for (int ty = 0; ty < ROOM_TILES_Y; ty++) {
            for (int tx = 0; tx < ROOM_TILES_X; tx++) {
                boolean isBorder = (tx == 0 || tx == ROOM_TILES_X - 1 || ty == 0 || ty == ROOM_TILES_Y - 1);
                Texture tile = isBorder ? wallTileTexture : floorTileTexture;
                batch.draw(tile, tx * TILE_SIZE, ty * TILE_SIZE);
            }
        }

        // 2. Render box (changes texture when jump is held)
        Texture activeTexture = isJumping ? boxJumpTexture : boxNormalTexture;
        int snappedX = boxPosition.getSnappedX(alpha);
        int snappedY = boxPosition.getSnappedY(alpha);
        batch.draw(activeTexture, snappedX, snappedY);
    }

    public PositionState getBoxPosition() {
        return boxPosition;
    }

    @Override
    public void dispose() {
        if (floorTileTexture != null) floorTileTexture.dispose();
        if (wallTileTexture != null) wallTileTexture.dispose();
        if (boxNormalTexture != null) boxNormalTexture.dispose();
        if (boxJumpTexture != null) boxJumpTexture.dispose();
    }
}
