package com.makeasaying.game.demo;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.makeasaying.data.world.RoomCache;
import com.makeasaying.data.world.RoomGridBuilder;
import com.makeasaying.data.world.RoomLoader;
import com.makeasaying.data.world.RoomWatcher;
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
import com.makeasaying.engine.entity.components.RoomScopeComponent;
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
import com.makeasaying.engine.world.RoomExit;
import com.makeasaying.engine.world.RoomLayerType;
import com.makeasaying.engine.world.RoomStateStore;
import com.makeasaying.engine.world.RoomTransitionHelper;
import com.makeasaying.engine.world.RoomTransitionManager;
import com.makeasaying.engine.world.SpawnPoint;
import com.makeasaying.engine.world.Tileset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Milestone 5b Demo Scene:
 * - Multi-room navigation (test_01 <-> test_02)
 * - Room format v2 with exit zones and preloading
 * - RoomCache with background worker preloading via Jobs
 * - RoomStateStore tracking per-room visited counts
 * - RoomTransitionManager running fade out, hold/swap, and fade in
 * - Carried player entity across transitions with velocity/timer reset
 * - Stale entity IDs fail cleanly on room change
 * - HUD showing room ID, transition state/tick, visit counts, cache contents, disabled exits
 */
public final class DemoSceneM5b implements Disposable {
    public static final int VIRTUAL_WIDTH = 320;
    public static final int VIRTUAL_HEIGHT = 180;
    public static final int TILE_SIZE = 16;

    private final World world;
    private final PlayerControlSystem controlSystem;
    private final PlatformSystem platformSystem;
    private final MovementSystem movementSystem;
    private final Camera camera;
    private final DefaultTextureProvider textureProvider;

    private final RoomCache roomCache = new RoomCache();
    private final RoomStateStore roomStateStore = new RoomStateStore();
    private final RoomTransitionManager transitionManager = new RoomTransitionManager();

    private final Map<String, Tileset> loadedTilesets = new LinkedHashMap<>();
    private final Map<String, TileGrid> roomGrids = new HashMap<>();

    private Room currentRoom;
    private TileGrid currentGrid;
    private RoomRenderer roomRenderer;
    private RoomWatcher roomWatcher;

    private Texture tilesetTextureA;
    private Texture tilesetTextureB;
    private Texture playerTexture;
    private Texture platformTexture;
    private Texture debugPixelTexture;
    private BitmapFont debugFont;

    private long playerId;
    private Jobs jobs;
    private Path roomsDir;

    public DemoSceneM5b(MovementTuning tuning, Jobs jobs) {
        this.world = new World();
        this.controlSystem = new PlayerControlSystem();
        this.platformSystem = new PlatformSystem();
        this.movementSystem = new MovementSystem(tuning);
        this.camera = new Camera(VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        this.textureProvider = new DefaultTextureProvider();
        this.jobs = jobs;

        findContentDirs();
        loadInitialContent();
        spawnPlayer();
        spawnRoomLocalEntities();
        initRenderResources();

        // Increment visit count for start room
        roomStateStore.incrementInt(currentRoom.id(), "visited");

        // Kick off initial neighbor preloading
        roomCache.preloadNeighbors(currentRoom, roomsDir, jobs, (id, r) -> {
            roomGrids.put(id, RoomGridBuilder.build(r, loadedTilesets, TILE_SIZE));
            Log.info(LogCategory.DATA, "Preloaded neighbor room %s into cache", id);
        }, err -> Log.error(LogCategory.DATA, "Preload failed: %s", err.getMessage()));
    }

    private void findContentDirs() {
        roomsDir = Path.of("content/rooms");
        if (!Files.exists(roomsDir)) {
            roomsDir = Path.of("..", "content/rooms");
        }
    }

    private void loadInitialContent() {
        Path tsDir = Path.of("content/tilesets");
        if (!Files.exists(tsDir)) tsDir = Path.of("..", "content/tilesets");

        try {
            Tileset tsA = TilesetLoader.load(tsDir.resolve("test_area.json"));
            Tileset tsB = TilesetLoader.load(tsDir.resolve("test_area_b.json"));
            loadedTilesets.put(tsA.id(), tsA);
            loadedTilesets.put(tsB.id(), tsB);
        } catch (IOException e) {
            Log.error(LogCategory.DATA, "Failed loading tilesets: %s", e, e.getMessage());
        }

        try {
            Path r1Path = roomsDir.resolve("test_01.json");
            currentRoom = RoomLoader.load(r1Path);
            roomCache.put(currentRoom, r1Path);
            currentGrid = RoomGridBuilder.build(currentRoom, loadedTilesets, TILE_SIZE);
            roomGrids.put(currentRoom.id(), currentGrid);
        } catch (IOException e) {
            Log.error(LogCategory.DATA, "Failed loading start room test_01: %s", e, e.getMessage());
            throw new RuntimeException("Initial room load failed", e);
        }

        this.roomRenderer = new RoomRenderer(textureProvider.createPlaceholderTexture());

        // Setup watcher on active room
        setupRoomWatcher(roomsDir.resolve("test_01.json"));
    }

    private void setupRoomWatcher(Path path) {
        roomWatcher = new RoomWatcher(path, loadedTilesets, jobs, result -> {
            if (result != null) {
                this.currentRoom = result.room();
                this.currentGrid = result.grid();
                this.roomGrids.put(result.room().id(), currentGrid);
                this.roomCache.put(result.room(), path);
                Log.info(LogCategory.DATA, "Hot-reloaded active room %s", result.room().id());
            }
        });
    }

    private void spawnPlayer() {
        SpawnPoint spawn = currentRoom.spawnPoints().stream()
            .filter(sp -> "player_start".equals(sp.id()))
            .findFirst()
            .orElse(currentRoom.spawnPoints().isEmpty() ? new SpawnPoint("default", 32, 16) : currentRoom.spawnPoints().get(0));

        this.playerId = world.createEntity();
        world.setBody(playerId, new BodyComponent(spawn.x(), spawn.y()));
        world.setCollider(playerId, new ColliderComponent(12, 36));
        world.setFacing(playerId, new FacingComponent(1));
        world.setController(playerId, new ControllerComponent(ControlSource.PLAYER));
        world.setJumpState(playerId, new JumpStateComponent());
        world.setRoomScope(playerId, new RoomScopeComponent(RoomScopeComponent.Scope.PERSISTENT_CARRIED));
        world.applyDeferredLifecycle();

        camera.reset(spawn.x(), spawn.y(), currentRoom.width(), currentRoom.height(), TILE_SIZE);
    }

    private void spawnRoomLocalEntities() {
        if ("test_01".equals(currentRoom.id())) {
            // Horizontal moving platform (160 -> 208)
            long horizId = world.createEntity();
            world.setPlatform(horizId, new PlatformComponent(
                280, 48, 360, 48, 32, 8, 1.0f, 15, PlatformComponent.CrushMode.BLOCK
            ));
            world.setRoomScope(horizId, new RoomScopeComponent(RoomScopeComponent.Scope.ROOM_LOCAL));

            // Vertical moving platform (480, 24 -> 72)
            long vertId = world.createEntity();
            world.setPlatform(vertId, new PlatformComponent(
                512, 32, 512, 96, 32, 8, 1.0f, 15, PlatformComponent.CrushMode.BLOCK
            ));
            world.setRoomScope(vertId, new RoomScopeComponent(RoomScopeComponent.Scope.ROOM_LOCAL));
        } else if ("test_02".equals(currentRoom.id())) {
            // Platform in room 2
            long pId = world.createEntity();
            world.setPlatform(pId, new PlatformComponent(
                120, 40, 184, 40, 32, 8, 1.0f, 15, PlatformComponent.CrushMode.BLOCK
            ));
            world.setRoomScope(pId, new RoomScopeComponent(RoomScopeComponent.Scope.ROOM_LOCAL));
        }
        world.applyDeferredLifecycle();
    }

    private void initRenderResources() {
        Path tsDir = Path.of("content/tilesets");
        if (!Files.exists(tsDir)) tsDir = Path.of("..", "content/tilesets");

        tilesetTextureA = new Texture(tsDir.resolve("test_area.png").toString());
        tilesetTextureA.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        textureProvider.register("test_area.png", tilesetTextureA);

        Path tsPathB = tsDir.resolve("test_area_b.png");
        if (Files.exists(tsPathB)) {
            tilesetTextureB = new Texture(tsPathB.toString());
            tilesetTextureB.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            textureProvider.register("test_area_b.png", tilesetTextureB);
        }

        // 12x36 cyan player placeholder
        Pixmap playerMap = new Pixmap(12, 36, Pixmap.Format.RGBA8888);
        playerMap.setColor(Color.valueOf("#00d2d3"));
        playerMap.fill();
        playerTexture = new Texture(playerMap);
        playerTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        playerMap.dispose();

        // 32x12 orange platform placeholder
        Pixmap platMap = new Pixmap(32, 12, Pixmap.Format.RGBA8888);
        platMap.setColor(Color.valueOf("#f39c12"));
        platMap.fill();
        platformTexture = new Texture(platMap);
        platformTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        platMap.dispose();

        // 1x1 white pixel for debug shapes & fade overlay
        Pixmap debugPixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        debugPixel.setColor(Color.WHITE);
        debugPixel.fill();
        debugPixelTexture = new Texture(debugPixel);
        debugPixel.dispose();

        debugFont = new BitmapFont();
        debugFont.getData().setScale(0.5f);
    }

    public boolean isWaitingForLoad() {
        return transitionManager.isWaitingForLoad();
    }

    public void tick(long currentTick, InputState playerInput) {
        // 1. Update transition state machine
        boolean advanceTick = transitionManager.update(
            playerId,
            world,
            currentRoom,
            () -> roomCache.isCached(transitionManager.getPendingExit() != null ? transitionManager.getPendingExit().targetRoom() : ""),
            exit -> performRoomSwap(exit)
        );

        if (!advanceTick) {
            // Tick advance is suspended while waiting for worker preload
            return;
        }

        // When transition is active, entity systems are frozen
        if (!transitionManager.isEntitySystemsFrozen()) {
            // 2. Input
            controlSystem.update(world, playerInput);

            // 4. Moving platforms
            platformSystem.update(world, currentGrid);

            // 5. Movement and Collision
            movementSystem.update(world, currentGrid);
        }

        // Update camera position clamped to active room bounds
        BodyComponent body = world.getBody(playerId);
        if (body != null && currentRoom != null) {
            camera.update(body.x, body.y, currentRoom.width(), currentRoom.height(), TILE_SIZE);
        }

        // 9. Events
        world.getEventQueue().dispatch();

        // 11. Lifecycle
        world.applyDeferredLifecycle();
    }

    private void performRoomSwap(RoomExit exit) {
        String targetId = exit.targetRoom();
        Room nextRoom = roomCache.get(targetId);
        if (nextRoom == null) {
            try {
                nextRoom = RoomLoader.load(roomsDir.resolve(targetId + ".json"));
                roomCache.put(nextRoom, roomsDir.resolve(targetId + ".json"));
            } catch (Exception e) {
                Log.error(LogCategory.DATA, "Failed loading room %s during swap: %s", targetId, e.getMessage());
                return;
            }
        }

        TileGrid nextGrid = roomGrids.computeIfAbsent(nextRoom.id(), id -> RoomGridBuilder.build(roomCache.get(id), loadedTilesets, TILE_SIZE));

        // Execute room swap for carried entities
        RoomTransitionHelper.executeRoomSwap(world, playerId, nextRoom, exit.targetSpawn(), nextGrid, roomStateStore);

        this.currentRoom = nextRoom;
        this.currentGrid = nextGrid;

        // Disable exit overlapping player's arrival position to prevent immediate bounce-back
        BodyComponent body = world.getBody(playerId);
        ColliderComponent collider = world.getCollider(playerId);
        if (body != null && collider != null) {
            for (RoomExit targetExit : currentRoom.exits()) {
                if (targetExit.overlaps(body.x + collider.offsetX, body.y + collider.offsetY, collider.width, collider.height)) {
                    transitionManager.disableExit(targetExit.id());
                }
            }
            // Snap camera directly to new position
            camera.reset(body.x, body.y, currentRoom.width(), currentRoom.height(), TILE_SIZE);
        }

        // Setup watcher on new room
        setupRoomWatcher(roomsDir.resolve(nextRoom.id() + ".json"));

        // Spawn room-local entities for the newly entered room
        spawnRoomLocalEntities();

        // Preload exit neighbors of the newly entered room
        roomCache.preloadNeighbors(currentRoom, roomsDir, jobs, (id, r) -> {
            roomGrids.put(id, RoomGridBuilder.build(r, loadedTilesets, TILE_SIZE));
            Log.info(LogCategory.DATA, "Preloaded neighbor room %s into cache", id);
        }, err -> Log.error(LogCategory.DATA, "Preload failed: %s", err.getMessage()));

        Log.info(LogCategory.ENGINE, "Swapped to room %s, spawn %s. Total visits: %d",
            currentRoom.id(), exit.targetSpawn(), roomStateStore.getInt(currentRoom.id(), "visited", 0));
    }

    public void pollWatcher() {
        if (roomWatcher != null) {
            roomWatcher.checkAndReload();
        }
    }

    public void render(SpriteBatch batch, float alpha) {
        if (transitionManager.isWaitingForLoad()) {
            // Render completely black during suspended preload wait
            return;
        }

        int camX = camera.getSnappedRenderX(alpha);
        int camY = camera.getSnappedRenderY(alpha);

        // Render background layers
        roomRenderer.renderLayer(batch, currentRoom, RoomLayerType.FAR_BACKGROUND, loadedTilesets, camX, camY, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, TILE_SIZE);
        roomRenderer.renderLayer(batch, currentRoom, RoomLayerType.NEAR_BACKGROUND, loadedTilesets, camX, camY, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, TILE_SIZE);

        // Render main collision tile layer
        roomRenderer.renderLayer(batch, currentRoom, RoomLayerType.MAIN, loadedTilesets, camX, camY, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, TILE_SIZE);

        // Render decoration layer
        roomRenderer.renderLayer(batch, currentRoom, RoomLayerType.DECORATION, loadedTilesets, camX, camY, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, TILE_SIZE);

        // Render exits (debug visualization: blue for active, gray for disabled)
        for (RoomExit exit : currentRoom.exits()) {
            int ex = exit.x() - camX;
            int ey = exit.y() - camY;
            boolean disabled = transitionManager.getDisabledExitIds().contains(exit.id());
            batch.setColor(disabled ? Color.DARK_GRAY : Color.CYAN);
            drawRectOutline(batch, ex, ey, exit.width(), exit.height());
            batch.setColor(Color.WHITE);
        }

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
        }

        // Render foreground layer
        roomRenderer.renderLayer(batch, currentRoom, RoomLayerType.FOREGROUND, loadedTilesets, camX, camY, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, TILE_SIZE);

        // Render full-screen transition fade overlay
        float fadeAlpha = transitionManager.getFadeAlpha();
        if (fadeAlpha > 0.001f) {
            batch.setColor(0f, 0f, 0f, fadeAlpha);
            batch.draw(debugPixelTexture, 0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
            batch.setColor(Color.WHITE);
        }

        // Render Debug HUD overlay
        if (body != null) {
            String hudText = String.format(
                "Room: %s | State: %s (%d) | Visits: %d\nCache: %s | Disabled Exits: %s\nPos: (%d, %d) | Grd: %b | Cam: (%d, %d)",
                currentRoom.id(),
                transitionManager.getState(),
                transitionManager.getTransitionTick(),
                roomStateStore.getInt(currentRoom.id(), "visited", 0),
                roomCache.getCachedRoomIds(),
                transitionManager.getDisabledExitIds(),
                body.x, body.y, body.grounded, camX, camY
            );
            debugFont.setColor(Color.YELLOW);
            debugFont.draw(batch, hudText, 6, VIRTUAL_HEIGHT - 6);
            debugFont.setColor(Color.WHITE);
        }
    }

    private void drawRectOutline(SpriteBatch batch, int x, int y, int w, int h) {
        batch.draw(debugPixelTexture, x, y, w, 1);
        batch.draw(debugPixelTexture, x, y + h - 1, w, 1);
        batch.draw(debugPixelTexture, x, y, 1, h);
        batch.draw(debugPixelTexture, x + w - 1, y, 1, h);
    }

    public World getWorld() {
        return world;
    }

    public TileGrid getGrid() {
        return currentGrid;
    }

    public Room getRoom() {
        return currentRoom;
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

    public RoomTransitionManager getTransitionManager() {
        return transitionManager;
    }

    public RoomCache getRoomCache() {
        return roomCache;
    }

    public RoomStateStore getRoomStateStore() {
        return roomStateStore;
    }

    public long getPlayerId() {
        return playerId;
    }

    @Override
    public void dispose() {
        if (tilesetTextureA != null) tilesetTextureA.dispose();
        if (tilesetTextureB != null) tilesetTextureB.dispose();
        if (playerTexture != null) playerTexture.dispose();
        if (platformTexture != null) platformTexture.dispose();
        if (debugPixelTexture != null) debugPixelTexture.dispose();
        if (debugFont != null) debugFont.dispose();
        if (textureProvider != null) textureProvider.dispose();
    }
}
