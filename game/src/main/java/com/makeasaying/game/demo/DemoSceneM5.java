package com.makeasaying.game.demo;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.makeasaying.data.world.RoomGridBuilder;
import com.makeasaying.data.world.RoomLoader;
import com.makeasaying.data.world.RoomWatcher;
import com.makeasaying.data.world.TileSheetGenerator;
import com.makeasaying.data.world.TilesetLoader;
import com.makeasaying.engine.async.Jobs;
import com.makeasaying.engine.entity.ControlSource;
import com.makeasaying.engine.entity.MovementTuning;
import com.makeasaying.engine.entity.TileGrid;
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
import com.makeasaying.engine.render.Camera;
import com.makeasaying.engine.render.DefaultTextureProvider;
import com.makeasaying.engine.render.RoomRenderer;
import com.makeasaying.engine.render.Snap;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomLayerType;
import com.makeasaying.engine.world.SpawnPoint;
import com.makeasaying.engine.world.Tileset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Milestone 5a Demo Scene:
 * - Loads authored test_area.json and test_01.json (at least 40 tiles wide)
 * - TileGrid constructed via RoomGridBuilder
 * - Camera follows player, clamped to room bounds, centered if room smaller than view
 * - Layer rendering via RoomRenderer through 320x180 virtual canvas with integer scaling
 * - Hot-reloads room via RoomWatcher and Jobs off-thread parse & validate
 */
public final class DemoSceneM5 implements Disposable {
    public static final int VIRTUAL_WIDTH = 320;
    public static final int VIRTUAL_HEIGHT = 180;
    public static final int TILE_SIZE = 16;

    private final World world;
    private final PlayerControlSystem controlSystem;
    private final PlatformSystem platformSystem;
    private final MovementSystem movementSystem;
    private final Camera camera;
    private final DefaultTextureProvider textureProvider;

    private Room room;
    private TileGrid grid;
    private final Map<String, Tileset> loadedTilesets = new LinkedHashMap<>();

    private RoomRenderer roomRenderer;
    private RoomWatcher roomWatcher;

    private Texture tilesetTexture;
    private Texture playerTexture;
    private Texture platformTexture;
    private Texture debugPixelTexture;
    private BitmapFont debugFont;

    private long playerId;
    private long horizPlatformId;
    private long vertPlatformId;

    public DemoSceneM5(MovementTuning tuning, Jobs jobs) {
        this.world = new World();
        this.controlSystem = new PlayerControlSystem();
        this.platformSystem = new PlatformSystem();
        this.movementSystem = new MovementSystem(tuning);
        this.camera = new Camera(VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        this.textureProvider = new DefaultTextureProvider();

        loadContent(jobs);
        spawnEntities();
        initRenderResources();
    }

    private void loadContent(Jobs jobs) {
        Path tsPath = Path.of("content/tilesets/test_area.json");
        if (!Files.exists(tsPath)) tsPath = Path.of("..", "content/tilesets/test_area.json");

        Path tsImagePath = Path.of("content/tilesets/test_area.png");
        if (!Files.exists(tsImagePath)) tsImagePath = Path.of("..", "content/tilesets/test_area.png");

        Path roomPath = Path.of("content/rooms/test_01.json");
        if (!Files.exists(roomPath)) roomPath = Path.of("..", "content/rooms/test_01.json");

        // Ensure tile sheet PNG exists
        if (!Files.exists(tsImagePath)) {
            try {
                TileSheetGenerator.generateToFile(tsImagePath);
            } catch (IOException e) {
                Log.error(LogCategory.DATA, "Failed generating tile sheet PNG: %s", e, e.getMessage());
            }
        }

        try {
            Tileset tileset = TilesetLoader.load(tsPath);
            loadedTilesets.put(tileset.id(), tileset);
            this.room = RoomLoader.load(roomPath);
            this.grid = RoomGridBuilder.build(room, loadedTilesets, TILE_SIZE);
        } catch (Exception e) {
            Log.error(LogCategory.DATA, "Failed loading room or tileset: %s", e, e.getMessage());
            throw new RuntimeException("Content load failed: " + e.getMessage(), e);
        }

        // Room hot-reloader
        this.roomWatcher = new RoomWatcher(roomPath, loadedTilesets, jobs, result -> {
            if (result != null) {
                this.room = result.room();
                this.grid = result.grid();
                Log.info(LogCategory.DATA, "Live hot-reload: room '%s' applied (%dx%d tiles)",
                    room.id(), room.width(), room.height());
            }
        });
    }

    private void spawnEntities() {
        // Find player spawn point in loaded room
        SpawnPoint spawn = room.spawnPoints().stream()
            .filter(sp -> "player_start".equals(sp.id()))
            .findFirst()
            .orElse(new SpawnPoint("default", 32, 16));

        playerId = world.createEntity();
        world.setBody(playerId, new BodyComponent(spawn.x(), spawn.y()));
        world.setCollider(playerId, new ColliderComponent(12, 36));
        world.setFacing(playerId, new FacingComponent(1));
        world.setController(playerId, new ControllerComponent(ControlSource.PLAYER));
        world.setJumpState(playerId, new JumpStateComponent());
        world.applyDeferredLifecycle();

        // Initialize camera at player spawn clamped to room
        camera.reset(spawn.x(), spawn.y(), room.width(), room.height(), TILE_SIZE);

        // Spawn horizontal moving platform in middle section
        horizPlatformId = world.createEntity();
        world.setPlatform(horizPlatformId, new PlatformComponent(
            280, 48, 360, 48, 32, 8, 1.0f, 15, PlatformComponent.CrushMode.BLOCK
        ));

        // Spawn descending moving platform
        vertPlatformId = world.createEntity();
        world.setPlatform(vertPlatformId, new PlatformComponent(
            512, 32, 512, 96, 32, 8, 1.0f, 15, PlatformComponent.CrushMode.BLOCK
        ));
        world.applyDeferredLifecycle();
    }

    private void initRenderResources() {
        Texture placeholder = textureProvider.createPlaceholderTexture();
        roomRenderer = new RoomRenderer(placeholder);

        // Load tileset texture
        com.badlogic.gdx.files.FileHandle imgHandle = com.badlogic.gdx.Gdx.files.internal("content/tilesets/test_area.png");
        if (!imgHandle.exists()) {
            imgHandle = com.badlogic.gdx.Gdx.files.internal("../content/tilesets/test_area.png");
        }
        tilesetTexture = new Texture(imgHandle);
        tilesetTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        tilesetTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);

        Tileset testTs = loadedTilesets.get("test_area");
        if (testTs != null) {
            roomRenderer.registerTileset(testTs, tilesetTexture);
        }

        // Procedural player sprite (12x36 cyan box with outline)
        Pixmap playerPixmap = new Pixmap(12, 36, Pixmap.Format.RGBA8888);
        playerPixmap.setColor(Color.valueOf("#3498db"));
        playerPixmap.fill();
        playerPixmap.setColor(Color.valueOf("#ffffff"));
        playerPixmap.drawRectangle(0, 0, 12, 36);
        playerTexture = new Texture(playerPixmap);
        playerPixmap.dispose();

        // Platform texture (32x8 amber box)
        Pixmap platPixmap = new Pixmap(32, 8, Pixmap.Format.RGBA8888);
        platPixmap.setColor(Color.valueOf("#e67e22"));
        platPixmap.fill();
        platPixmap.setColor(Color.valueOf("#f39c12"));
        platPixmap.drawRectangle(0, 0, 32, 8);
        platformTexture = new Texture(platPixmap);
        platPixmap.dispose();

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
        // 2. Input
        controlSystem.update(world, playerInput);

        // 4. Moving platforms
        platformSystem.update(world, grid);

        // 5. Movement and Collision
        movementSystem.update(world, grid);

        // Update camera position clamped to room bounds
        BodyComponent body = world.getBody(playerId);
        if (body != null) {
            camera.update(body.x, body.y, room.width(), room.height(), TILE_SIZE);
        }

        // 9. Events
        world.getEventQueue().dispatch();

        // 11. Lifecycle
        world.applyDeferredLifecycle();
    }

    public void pollWatcher() {
        if (roomWatcher != null) {
            roomWatcher.checkAndReload();
        }
    }

    public void render(SpriteBatch batch, float alpha) {
        int camX = camera.getSnappedRenderX(alpha);
        int camY = camera.getSnappedRenderY(alpha);

        // Render background layers
        roomRenderer.renderLayer(batch, room, RoomLayerType.FAR_BACKGROUND, loadedTilesets, camX, camY, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, TILE_SIZE);
        roomRenderer.renderLayer(batch, room, RoomLayerType.NEAR_BACKGROUND, loadedTilesets, camX, camY, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, TILE_SIZE);

        // Render main collision tile layer
        roomRenderer.renderLayer(batch, room, RoomLayerType.MAIN, loadedTilesets, camX, camY, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, TILE_SIZE);

        // Render decoration layer
        roomRenderer.renderLayer(batch, room, RoomLayerType.DECORATION, loadedTilesets, camX, camY, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, TILE_SIZE);

        // Render moving platforms
        for (int i = 1; i < World.MAX_ENTITIES; i++) {
            if (!world.isAliveIndex(i)) continue;
            PlatformComponent plat = world.getPlatformByIndex(i);
            if (plat == null) continue;

            int pX = Snap.lerpAndSnap(plat.prevX, plat.currentX, alpha) - camX;
            int pY = Snap.lerpAndSnap(plat.prevY, plat.currentY, alpha) - camY;
            batch.setColor(Color.valueOf("#f39c12"));
            batch.draw(platformTexture, pX, pY, plat.width, plat.height);
            batch.setColor(Color.WHITE);
        }

        // Render Player
        BodyComponent body = world.getBody(playerId);
        ColliderComponent collider = world.getCollider(playerId);
        JumpStateComponent jumpState = world.getJumpState(playerId);

        if (body != null && collider != null) {
            int renderX = Snap.lerpAndSnap(body.prevX, body.x, alpha) - camX;
            int renderY = Snap.lerpAndSnap(body.prevY, body.y, alpha) - camY;

            int drawX = renderX + collider.offsetX;
            int drawY = renderY + collider.offsetY;

            // Draw player sprite
            batch.draw(playerTexture, drawX, drawY);

            // Draw 1px collider outline (green)
            batch.setColor(Color.GREEN);
            drawRectOutline(batch, drawX, drawY, collider.width, collider.height);

            // Draw slope sample columns: col1 = body.x - 1, col2 = body.x (yellow)
            batch.setColor(Color.YELLOW);
            batch.draw(debugPixelTexture, renderX - 1, renderY - 2, 1, 4);
            batch.draw(debugPixelTexture, renderX, renderY - 2, 1, 4);

            // Draw floor-height line (red 6px line under feet)
            batch.setColor(Color.RED);
            batch.draw(debugPixelTexture, renderX - 3, renderY, 6, 1);
            batch.setColor(Color.WHITE);

            // Render foreground layer
            roomRenderer.renderLayer(batch, room, RoomLayerType.FOREGROUND, loadedTilesets, camX, camY, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, TILE_SIZE);

            // Debug text overlay (fixed to screen coordinates)
            String debugText = String.format("Pos:(%d,%d) Cam:(%d,%d) Room:%dx%d\nGrd:%b Support:%d Coy:%d Buf:%d",
                body.x, body.y, camX, camY, room.width(), room.height(),
                body.grounded, body.supportId,
                jumpState != null ? jumpState.coyoteTimer : 0,
                jumpState != null ? jumpState.jumpBufferTimer : 0);
            debugFont.setColor(Color.WHITE);
            debugFont.draw(batch, debugText, 8, VIRTUAL_HEIGHT - 6);
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

    public Room getRoom() {
        return room;
    }

    public Camera getCamera() {
        return camera;
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
        if (tilesetTexture != null) tilesetTexture.dispose();
        if (playerTexture != null) playerTexture.dispose();
        if (platformTexture != null) platformTexture.dispose();
        if (debugPixelTexture != null) debugPixelTexture.dispose();
        if (debugFont != null) debugFont.dispose();
        if (textureProvider != null) textureProvider.dispose();
    }
}
