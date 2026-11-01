package com.makeasaying.editor.map;

import com.makeasaying.editor.core.command.UndoStack;
import com.makeasaying.editor.map.dialogs.NewRoomModel;
import com.makeasaying.editor.map.dialogs.SaveConfirmModel;
import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.editor.map.hotreload.EditorHotReloadHandler;
import com.makeasaying.editor.map.tools.EditorTool;
import com.makeasaying.editor.map.tools.PencilTool;
import com.makeasaying.editor.map.validator.EditorValidatorPresenter;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.WorldSnapshot;
import com.makeasaying.engine.world.RoomLayerType;

import java.nio.file.Path;

/**
 * Encapsulates in-memory editor runtime state per M6a brief.
 */
public final class RoomEditorState {
    private RoomDocument document;
    private Path currentFilePath;

    private RoomLayerType activeLayer = RoomLayerType.MAIN;
    private String activeTilesetAlias = "a";
    private String activeTileToken = "a:tile_01";
    private EditorTool activeTool = new PencilTool();

    private boolean showGrid = true;
    private boolean showCollision = true;
    private boolean showViewRect = true;
    private boolean dimOtherLayers = true;

    private float camX = 0f;
    private float camY = 0f;
    private int zoom = 2; // 1x to 8x

    // 320x180 game viewport preview rect position
    private int viewRectX = 0;
    private int viewRectY = 0;

    // Play mode state
    private boolean isPlaying = false;
    private World playWorld;
    private WorldSnapshot playSnapshot;
    private long playPlayerId = 0L;

    // Sub-models
    private final NewRoomModel newRoomModel = new NewRoomModel();
    private final SaveConfirmModel saveConfirmModel = new SaveConfirmModel();
    private final EditorValidatorPresenter validatorPresenter = new EditorValidatorPresenter();
    private EditorHotReloadHandler hotReloadHandler;

    public RoomEditorState(RoomDocument initialDoc, Path initialPath) {
        this.document = initialDoc != null ? initialDoc : new RoomDocument("new_room", 20, 12);
        this.currentFilePath = initialPath;
    }

    public RoomDocument getDocument() { return document; }
    public void setDocument(RoomDocument document) { this.document = document; }

    public Path getCurrentFilePath() { return currentFilePath; }
    public void setCurrentFilePath(Path path) { this.currentFilePath = path; }

    public RoomLayerType getActiveLayer() { return activeLayer; }
    public void setActiveLayer(RoomLayerType layer) { this.activeLayer = layer; }

    public String getActiveTilesetAlias() { return activeTilesetAlias; }
    public void setActiveTilesetAlias(String alias) { this.activeTilesetAlias = alias; }

    public String getActiveTileToken() { return activeTileToken; }
    public void setActiveTileToken(String token) { this.activeTileToken = token; }

    public EditorTool getActiveTool() { return activeTool; }
    public void setActiveTool(EditorTool tool) { this.activeTool = tool; }

    public boolean isShowGrid() { return showGrid; }
    public void setShowGrid(boolean showGrid) { this.showGrid = showGrid; }

    public boolean isShowCollision() { return showCollision; }
    public void setShowCollision(boolean showCollision) { this.showCollision = showCollision; }

    public boolean isShowViewRect() { return showViewRect; }
    public void setShowViewRect(boolean showViewRect) { this.showViewRect = showViewRect; }

    public boolean isDimOtherLayers() { return dimOtherLayers; }
    public void setDimOtherLayers(boolean dim) { this.dimOtherLayers = dim; }

    public float getCamX() { return camX; }
    public void setCamX(float camX) { this.camX = camX; }

    public float getCamY() { return camY; }
    public void setCamY(float camY) { this.camY = camY; }

    public int getZoom() { return zoom; }
    public void setZoom(int zoom) { this.zoom = Math.clamp(zoom, 1, 8); }

    public int getViewRectX() { return viewRectX; }
    public void setViewRectX(int x) { this.viewRectX = x; }

    public int getViewRectY() { return viewRectY; }
    public void setViewRectY(int y) { this.viewRectY = y; }

    public boolean isPlaying() { return isPlaying; }
    public void setPlaying(boolean playing) { this.isPlaying = playing; }

    public World getPlayWorld() { return playWorld; }
    public void setPlayWorld(World playWorld) { this.playWorld = playWorld; }

    public WorldSnapshot getPlaySnapshot() { return playSnapshot; }
    public void setPlaySnapshot(WorldSnapshot snapshot) { this.playSnapshot = snapshot; }

    public long getPlayPlayerId() { return playPlayerId; }
    public void setPlayPlayerId(long id) { this.playPlayerId = id; }

    public NewRoomModel getNewRoomModel() { return newRoomModel; }
    public SaveConfirmModel getSaveConfirmModel() { return saveConfirmModel; }
    public EditorValidatorPresenter getValidatorPresenter() { return validatorPresenter; }

    public EditorHotReloadHandler getHotReloadHandler() { return hotReloadHandler; }
    public void setHotReloadHandler(EditorHotReloadHandler handler) { this.hotReloadHandler = handler; }
}
