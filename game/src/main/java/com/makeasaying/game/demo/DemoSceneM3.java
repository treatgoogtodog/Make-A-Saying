package com.makeasaying.game.demo;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.makeasaying.engine.entity.ControlSource;
import com.makeasaying.engine.entity.MovementTuning;
import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.engine.entity.TileShape;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.ControllerComponent;
import com.makeasaying.engine.entity.components.FacingComponent;
import com.makeasaying.engine.entity.components.InputStateComponent;
import com.makeasaying.engine.entity.components.JumpStateComponent;
import com.makeasaying.engine.entity.systems.MovementSystem;
import com.makeasaying.engine.entity.systems.PlayerControlSystem;
import com.makeasaying.engine.input.InputState;
import com.makeasaying.engine.render.Snap;

/**
 * ============================================================================
 * TEMPORARY DEMO SCENE - M3 ONLY
 * Deletion point: To be replaced entirely in M5 with Data/Room loaders.
 * ============================================================================
 * Hard-coded level featuring:
 * - Solid perimeter walls, floor, and ceiling
 * - Platforms at several heights
 * - Gap for coyote time testing
 * - Ceiling notch/gap for ceiling corner correction testing
 * - Controllable player entity with debug overlay for position/velocity/collider
 */
public final class DemoSceneM3 implements Disposable {
    public static final int ROOM_TILES_X = 20;
    public static final int ROOM_TILES_Y = 12;
    public static final int TILE_SIZE = 16;

    private final World world;
    private final TileGrid grid;
    private final PlayerControlSystem controlSystem;
    private final MovementSystem movementSystem;

    private long playerId;

    private Texture floorTileTexture;
    private Texture wallTileTexture;
    private Texture playerTexture;
    private Texture colliderBoxTexture;
    private BitmapFont debugFont;

    public DemoSceneM3(MovementTuning tuning) {
        this.world = new World();
        this.grid = new TileGrid(ROOM_TILES_X, ROOM_TILES_Y, TILE_SIZE);
        this.controlSystem = new PlayerControlSystem();
        this.movementSystem = new MovementSystem(tuning);

        buildTestLevel();
        spawnPlayer();
        generateProceduralTextures();
    }

    private void buildTestLevel() {
        grid.clear();

        // 1. Perimeter walls, floor, and ceiling
        for (int x = 0; x < ROOM_TILES_X; x++) {
            grid.setTile(x, 0, TileShape.SOLID); // Floor
            grid.setTile(x, ROOM_TILES_Y - 1, TileShape.SOLID); // Ceiling
        }
        for (int y = 0; y < ROOM_TILES_Y; y++) {
            grid.setTile(0, y, TileShape.SOLID); // Left wall
            grid.setTile(ROOM_TILES_X - 1, y, TileShape.SOLID); // Right wall
        }

        // 2. Mid-height platforms
        for (int x = 4; x <= 8; x++) grid.setTile(x, 3, TileShape.SOLID);
        for (int x = 11; x <= 16; x++) grid.setTile(x, 5, TileShape.SOLID);

        // 3. High platform with coyote test gap
        for (int x = 3; x <= 7; x++) grid.setTile(x, 7, TileShape.SOLID);

        // 4. Ceiling corner correction test: ceiling at y=9 with 1-tile gap at x=14
        for (int x = 9; x <= 13; x++) grid.setTile(x, 9, TileShape.SOLID);
        for (int x = 15; x <= 17; x++) grid.setTile(x, 9, TileShape.SOLID);
        // Note: x=14 is an open 1-tile gap!
    }

    private void spawnPlayer() {
        playerId = world.createEntity();
        world.setBody(playerId, new BodyComponent(32, 16)); // Spawn feet on floor
        world.setCollider(playerId, new ColliderComponent(12, 36));
        world.setFacing(playerId, new FacingComponent(1));
        world.setController(playerId, new ControllerComponent(ControlSource.PLAYER));
        world.setJumpState(playerId, new JumpStateComponent());

        // Deferred spawns processed immediately on setup
        world.applyDeferredLifecycle();
    }

    private void generateProceduralTextures() {
        Pixmap floorPixmap = new Pixmap(TILE_SIZE, TILE_SIZE, Pixmap.Format.RGBA8888);
        floorPixmap.setColor(Color.valueOf("#22232a"));
        floorPixmap.fill();
        floorPixmap.setColor(Color.valueOf("#2c2d38"));
        floorPixmap.drawRectangle(0, 0, TILE_SIZE, TILE_SIZE);
        floorTileTexture = new Texture(floorPixmap);
        floorPixmap.dispose();

        Pixmap wallPixmap = new Pixmap(TILE_SIZE, TILE_SIZE, Pixmap.Format.RGBA8888);
        wallPixmap.setColor(Color.valueOf("#3a3f58"));
        wallPixmap.fill();
        wallPixmap.setColor(Color.valueOf("#565d7e"));
        wallPixmap.drawRectangle(0, 0, TILE_SIZE, TILE_SIZE);
        wallTileTexture = new Texture(wallPixmap);
        wallPixmap.dispose();

        // Player sprite placeholder (12x36 magenta/blue character)
        Pixmap playerPixmap = new Pixmap(12, 36, Pixmap.Format.RGBA8888);
        playerPixmap.setColor(Color.valueOf("#4d9be6"));
        playerPixmap.fill();
        playerPixmap.setColor(Color.valueOf("#ffffff"));
        playerPixmap.drawRectangle(0, 0, 12, 36);
        playerTexture = new Texture(playerPixmap);
        playerPixmap.dispose();

        // 1px white texture for collider wireframe
        Pixmap wirePixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        wirePixmap.setColor(Color.valueOf("#00ff66"));
        wirePixmap.fill();
        colliderBoxTexture = new Texture(wirePixmap);
        wirePixmap.dispose();

        debugFont = new BitmapFont();
        debugFont.getData().setScale(0.5f);
    }

    public void tick(long currentTick, InputState playerInput) {
        // System execution order per UPDATE_ORDER.md
        // 1. Input-to-InputState
        controlSystem.update(world, playerInput);

        // 2. Movement and Collision
        movementSystem.update(world, grid);

        // 3. Event Dispatch
        world.getEventQueue().dispatch();

        // 4. Deferred Spawn/Despawn
        world.applyDeferredLifecycle();
    }

    public void render(SpriteBatch batch, float alpha) {
        // 1. Render Tile Grid
        for (int ty = 0; ty < ROOM_TILES_Y; ty++) {
            for (int tx = 0; tx < ROOM_TILES_X; tx++) {
                boolean solid = grid.isSolid(tx, ty);
                Texture tex = solid ? wallTileTexture : floorTileTexture;
                batch.draw(tex, tx * TILE_SIZE, ty * TILE_SIZE);
            }
        }

        // 2. Render Player with subpixel interpolation & pixel snap
        BodyComponent body = world.getBody(playerId);
        ColliderComponent collider = world.getCollider(playerId);
        FacingComponent facing = world.getFacing(playerId);
        JumpStateComponent jumpState = world.getJumpState(playerId);

        if (body != null && collider != null) {
            int renderX = Snap.lerpAndSnap(body.prevX, body.x, alpha);
            int renderY = Snap.lerpAndSnap(body.prevY, body.y, alpha);

            int drawX = renderX + collider.offsetX;
            int drawY = renderY + collider.offsetY;

            // Draw player sprite
            batch.draw(playerTexture, drawX, drawY);

            // Draw 1px collider outline (debug)
            drawRectOutline(batch, drawX, drawY, collider.width, collider.height);

            // Draw debug text overlay
            String debugText = String.format("Pos:(%d,%d) Vel:(%.1f,%.1f)\nGrd:%b Coy:%d Buf:%d",
                body.x, body.y, body.vx, body.vy, body.grounded,
                jumpState != null ? jumpState.coyoteTimer : 0,
                jumpState != null ? jumpState.jumpBufferTimer : 0);
            debugFont.setColor(Color.WHITE);
            debugFont.draw(batch, debugText, 16, ROOM_TILES_Y * TILE_SIZE - 4);
        }
    }

    private void drawRectOutline(SpriteBatch batch, int x, int y, int w, int h) {
        batch.draw(colliderBoxTexture, x, y, w, 1);         // Bottom
        batch.draw(colliderBoxTexture, x, y + h - 1, w, 1); // Top
        batch.draw(colliderBoxTexture, x, y, 1, h);         // Left
        batch.draw(colliderBoxTexture, x + w - 1, y, 1, h); // Right
    }

    public World getWorld() {
        return world;
    }

    public TileGrid getGrid() {
        return grid;
    }

    public MovementSystem getMovementSystem() {
        return movementSystem;
    }

    public long getPlayerId() {
        return playerId;
    }

    @Override
    public void dispose() {
        if (floorTileTexture != null) floorTileTexture.dispose();
        if (wallTileTexture != null) wallTileTexture.dispose();
        if (playerTexture != null) playerTexture.dispose();
        if (colliderBoxTexture != null) colliderBoxTexture.dispose();
        if (debugFont != null) debugFont.dispose();
    }
}
