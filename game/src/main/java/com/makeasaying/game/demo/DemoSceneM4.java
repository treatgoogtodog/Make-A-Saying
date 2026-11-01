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
import com.makeasaying.engine.entity.components.PlatformComponent;
import com.makeasaying.engine.entity.systems.MovementSystem;
import com.makeasaying.engine.entity.systems.PlatformSystem;
import com.makeasaying.engine.entity.systems.PlayerControlSystem;
import com.makeasaying.engine.input.InputState;
import com.makeasaying.engine.render.Snap;

/**
 * ============================================================================
 * TEMPORARY DEMO SCENE - M4 ONLY (Slopes and Platforms)
 * Deletion point: To be replaced entirely in M5 with Data/Room loaders.
 * ============================================================================
 * Level featuring:
 * - Slopes: SLOPE_45_UP_RIGHT and SLOPE_45_UP_LEFT
 * - One-way platform ledge (jump through, drop through)
 * - Moving platform (ping-pong trajectory, carrying rider)
 * - Debug overlay showing: support platform ID, slope sample columns, floor-height line
 */
public final class DemoSceneM4 implements Disposable {
    public static final int ROOM_TILES_X = 20;
    public static final int ROOM_TILES_Y = 12;
    public static final int TILE_SIZE = 16;

    private final World world;
    private final TileGrid grid;
    private final PlayerControlSystem controlSystem;
    private final PlatformSystem platformSystem;
    private final MovementSystem movementSystem;

    private long playerId;
    private long horizPlatformId;
    private long vertPlatformId;

    private Texture floorTileTexture;
    private Texture wallTileTexture;
    private Texture slopeUpRightTexture;
    private Texture slopeUpLeftTexture;
    private Texture oneWayTexture;
    private Texture platformTexture;
    private Texture playerTexture;
    private Texture debugPixelTexture;
    private BitmapFont debugFont;

    public DemoSceneM4(MovementTuning tuning) {
        this.world = new World();
        this.grid = new TileGrid(ROOM_TILES_X, ROOM_TILES_Y, TILE_SIZE);
        this.controlSystem = new PlayerControlSystem();
        this.platformSystem = new PlatformSystem();
        this.movementSystem = new MovementSystem(tuning);

        buildTestLevel();
        spawnEntities();
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

        // 2. Slope UP_RIGHT hill at tx=2, elevated flat at tx=3..4, slope UP_LEFT descent at tx=5
        grid.setTile(2, 1, TileShape.SLOPE_45_UP_RIGHT);
        grid.setTile(3, 1, TileShape.SOLID);
        grid.setTile(4, 1, TileShape.SOLID);
        grid.setTile(5, 1, TileShape.SLOPE_45_UP_LEFT);

        // 3. One-way platform ledge at tx=8..12, ty=3 (y: 48)
        for (int x = 8; x <= 12; x++) {
            grid.setTile(x, 3, TileShape.ONE_WAY);
        }

        // 4. Solid ledge at tx=14..18, ty=5 (y: 80)
        for (int x = 14; x <= 18; x++) {
            grid.setTile(x, 5, TileShape.SOLID);
        }
    }

    private void spawnEntities() {
        // Player at flat floor
        playerId = world.createEntity();
        world.setBody(playerId, new BodyComponent(24, 16));
        world.setCollider(playerId, new ColliderComponent(12, 36));
        world.setFacing(playerId, new FacingComponent(1));
        world.setController(playerId, new ControllerComponent(ControlSource.PLAYER));
        world.setJumpState(playerId, new JumpStateComponent());

        // Horizontal moving platform (x: 48 -> 112, y: 88, width: 36, height: 8, speed: 1.0)
        horizPlatformId = world.createEntity();
        world.setPlatform(horizPlatformId, new PlatformComponent(
            48, 88, 112, 88, 36, 8, 1.0f, 15, PlatformComponent.CrushMode.BLOCK
        ));

        // Vertical moving platform (x: 140, y: 32 -> 96, width: 32, height: 8, speed: 1.0)
        vertPlatformId = world.createEntity();
        world.setPlatform(vertPlatformId, new PlatformComponent(
            140, 32, 140, 96, 32, 8, 1.0f, 15, PlatformComponent.CrushMode.BLOCK
        ));

        world.applyDeferredLifecycle();
    }

    private void generateProceduralTextures() {
        // Floor tile (dark gray)
        Pixmap floorPixmap = new Pixmap(TILE_SIZE, TILE_SIZE, Pixmap.Format.RGBA8888);
        floorPixmap.setColor(Color.valueOf("#22232a"));
        floorPixmap.fill();
        floorPixmap.setColor(Color.valueOf("#2c2d38"));
        floorPixmap.drawRectangle(0, 0, TILE_SIZE, TILE_SIZE);
        floorTileTexture = new Texture(floorPixmap);
        floorPixmap.dispose();

        // Wall tile (slate blue)
        Pixmap wallPixmap = new Pixmap(TILE_SIZE, TILE_SIZE, Pixmap.Format.RGBA8888);
        wallPixmap.setColor(Color.valueOf("#3a3f58"));
        wallPixmap.fill();
        wallPixmap.setColor(Color.valueOf("#565d7e"));
        wallPixmap.drawRectangle(0, 0, TILE_SIZE, TILE_SIZE);
        wallTileTexture = new Texture(wallPixmap);
        wallPixmap.dispose();

        // Slope UP_RIGHT (Y-up: bottom-left to top-right diagonal)
        Pixmap surPixmap = new Pixmap(TILE_SIZE, TILE_SIZE, Pixmap.Format.RGBA8888);
        surPixmap.setColor(0, 0, 0, 0);
        surPixmap.fill();
        surPixmap.setColor(Color.valueOf("#4a5070"));
        for (int x = 0; x < TILE_SIZE; x++) {
            // Y-up: column x has height x+1 from bottom
            int h = x + 1;
            surPixmap.drawLine(x, TILE_SIZE - 1, x, TILE_SIZE - h);
        }
        slopeUpRightTexture = new Texture(surPixmap);
        surPixmap.dispose();

        // Slope UP_LEFT (Y-up: top-left to bottom-right diagonal)
        Pixmap sulPixmap = new Pixmap(TILE_SIZE, TILE_SIZE, Pixmap.Format.RGBA8888);
        sulPixmap.setColor(0, 0, 0, 0);
        sulPixmap.fill();
        sulPixmap.setColor(Color.valueOf("#4a5070"));
        for (int x = 0; x < TILE_SIZE; x++) {
            // Y-up: column x has height 16 - x from bottom
            int h = 16 - x;
            sulPixmap.drawLine(x, TILE_SIZE - 1, x, TILE_SIZE - h);
        }
        slopeUpLeftTexture = new Texture(sulPixmap);
        sulPixmap.dispose();

        // One-Way platform (thin top bar with dashed lines)
        Pixmap oneWayPixmap = new Pixmap(TILE_SIZE, TILE_SIZE, Pixmap.Format.RGBA8888);
        oneWayPixmap.setColor(0, 0, 0, 0);
        oneWayPixmap.fill();
        oneWayPixmap.setColor(Color.valueOf("#e5a823"));
        oneWayPixmap.fillRectangle(0, 0, TILE_SIZE, 4); // Pixmap 0 is top
        oneWayTexture = new Texture(oneWayPixmap);
        oneWayPixmap.dispose();

        // Moving platform texture (orange/gold metallic)
        Pixmap platPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        platPixmap.setColor(Color.valueOf("#f39c12"));
        platPixmap.fill();
        platformTexture = new Texture(platPixmap);
        platPixmap.dispose();

        // Player placeholder (12x36 cyan)
        Pixmap playerPixmap = new Pixmap(12, 36, Pixmap.Format.RGBA8888);
        playerPixmap.setColor(Color.valueOf("#3498db"));
        playerPixmap.fill();
        playerPixmap.setColor(Color.valueOf("#ffffff"));
        playerPixmap.drawRectangle(0, 0, 12, 36);
        playerTexture = new Texture(playerPixmap);
        playerPixmap.dispose();

        // 1px debug pixel
        Pixmap debugPixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        debugPixel.setColor(Color.WHITE);
        debugPixel.fill();
        debugPixelTexture = new Texture(debugPixel);
        debugPixel.dispose();

        debugFont = new BitmapFont();
        debugFont.getData().setScale(0.5f);
    }

    public void tick(long currentTick, InputState playerInput) {
        // System execution order per UPDATE_ORDER.md
        // 2. Input collection
        controlSystem.update(world, playerInput);

        // 4. Moving platforms update and carry riders
        platformSystem.update(world, grid);

        // 5. Movement and Collision
        movementSystem.update(world, grid);

        // 9. Event dispatch
        world.getEventQueue().dispatch();

        // 11. Deferred spawn/despawn
        world.applyDeferredLifecycle();
    }

    public void render(SpriteBatch batch, float alpha) {
        // 1. Render Tile Grid
        for (int ty = 0; ty < ROOM_TILES_Y; ty++) {
            for (int tx = 0; tx < ROOM_TILES_X; tx++) {
                TileShape shape = grid.getShape(tx, ty);
                int drawX = tx * TILE_SIZE;
                int drawY = ty * TILE_SIZE;

                switch (shape) {
                    case SOLID -> batch.draw(wallTileTexture, drawX, drawY);
                    case SLOPE_45_UP_RIGHT -> batch.draw(slopeUpRightTexture, drawX, drawY);
                    case SLOPE_45_UP_LEFT -> batch.draw(slopeUpLeftTexture, drawX, drawY);
                    case ONE_WAY -> batch.draw(oneWayTexture, drawX, drawY);
                    case EMPTY -> batch.draw(floorTileTexture, drawX, drawY);
                }
            }
        }

        // 2. Render Moving Platforms
        for (int i = 1; i < World.MAX_ENTITIES; i++) {
            if (!world.isAliveIndex(i)) continue;
            PlatformComponent plat = world.getPlatformByIndex(i);
            if (plat == null) continue;

            int pX = Snap.lerpAndSnap(plat.prevX, plat.currentX, alpha);
            int pY = Snap.lerpAndSnap(plat.prevY, plat.currentY, alpha);
            batch.setColor(Color.valueOf("#f39c12"));
            batch.draw(platformTexture, pX, pY, plat.width, plat.height);
            batch.setColor(Color.WHITE);
        }

        // 3. Render Player with subpixel interpolation & pixel snap
        BodyComponent body = world.getBody(playerId);
        ColliderComponent collider = world.getCollider(playerId);
        JumpStateComponent jumpState = world.getJumpState(playerId);

        if (body != null && collider != null) {
            int renderX = Snap.lerpAndSnap(body.prevX, body.x, alpha);
            int renderY = Snap.lerpAndSnap(body.prevY, body.y, alpha);

            int drawX = renderX + collider.offsetX;
            int drawY = renderY + collider.offsetY;

            // Draw player sprite
            batch.draw(playerTexture, drawX, drawY);

            // Draw 1px collider outline (green)
            batch.setColor(Color.GREEN);
            drawRectOutline(batch, drawX, drawY, collider.width, collider.height);

            // Draw slope sample columns: col1 = body.x - 1, col2 = body.x (yellow 1px vertical line)
            batch.setColor(Color.YELLOW);
            batch.draw(debugPixelTexture, body.x - 1, body.y - 2, 1, 4);
            batch.draw(debugPixelTexture, body.x, body.y - 2, 1, 4);

            // Draw floor-height line (red 6px line under feet)
            batch.setColor(Color.RED);
            batch.draw(debugPixelTexture, body.x - 3, body.y, 6, 1);
            batch.setColor(Color.WHITE);

            // Draw debug text overlay
            String debugText = String.format("Pos:(%d,%d) Vel:(%.1f,%.1f)\nGrd:%b Support:%d Coy:%d Buf:%d",
                body.x, body.y, body.vx, body.vy, body.grounded, body.supportId,
                jumpState != null ? jumpState.coyoteTimer : 0,
                jumpState != null ? jumpState.jumpBufferTimer : 0);
            debugFont.setColor(Color.WHITE);
            debugFont.draw(batch, debugText, 16, ROOM_TILES_Y * TILE_SIZE - 4);
        }
    }

    private void drawRectOutline(SpriteBatch batch, int x, int y, int w, int h) {
        batch.draw(debugPixelTexture, x, y, w, 1);         // Bottom
        batch.draw(debugPixelTexture, x, y + h - 1, w, 1); // Top
        batch.draw(debugPixelTexture, x, y, 1, h);         // Left
        batch.draw(debugPixelTexture, x + w - 1, y, 1, h); // Right
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

    public PlatformSystem getPlatformSystem() {
        return platformSystem;
    }

    public long getPlayerId() {
        return playerId;
    }

    @Override
    public void dispose() {
        if (floorTileTexture != null) floorTileTexture.dispose();
        if (wallTileTexture != null) wallTileTexture.dispose();
        if (slopeUpRightTexture != null) slopeUpRightTexture.dispose();
        if (slopeUpLeftTexture != null) slopeUpLeftTexture.dispose();
        if (oneWayTexture != null) oneWayTexture.dispose();
        if (platformTexture != null) platformTexture.dispose();
        if (playerTexture != null) playerTexture.dispose();
        if (debugPixelTexture != null) debugPixelTexture.dispose();
        if (debugFont != null) debugFont.dispose();
    }
}
