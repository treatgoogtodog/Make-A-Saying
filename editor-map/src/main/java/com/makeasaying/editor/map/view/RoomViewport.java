package com.makeasaying.editor.map.view;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Disposable;
import com.makeasaying.editor.core.raster.GeometryRasterizer;
import com.makeasaying.editor.map.RoomEditorState;
import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.engine.entity.TileShape;
import com.makeasaying.engine.render.RoomRenderer;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomExit;
import com.makeasaying.engine.world.RoomLayer;
import com.makeasaying.engine.world.RoomLayerType;
import com.makeasaying.engine.world.SpawnPoint;
import com.makeasaying.engine.world.TileDef;
import com.makeasaying.engine.world.Tileset;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.List;
import java.util.Map;

/**
 * Offscreen viewport renderer for room editing per M6a brief.
 * Renders via real RoomRenderer into a dynamically-sized FrameBuffer,
 * supports integer zoom (1x-8x around cursor), middle-drag and space-drag pan,
 * grid overlay, collision shapes, view rect, spawn points, and tool previews.
 */
public final class RoomViewport implements Disposable {
    private static final int TILE_SIZE = 16;
    private static final int GAME_VIEW_W = 320;
    private static final int GAME_VIEW_H = 180;

    private static final RoomLayerType[] RENDER_ORDER = {
        RoomLayerType.FAR_BACKGROUND,
        RoomLayerType.NEAR_BACKGROUND,
        RoomLayerType.MAIN,
        RoomLayerType.DECORATION,
        RoomLayerType.FOREGROUND
    };

    private FrameBuffer fbo;
    private final SpriteBatch batch;
    private final Texture pixelTexture;
    private final BitmapFont font;

    private float lastMouseX = 0f;
    private float lastMouseY = 0f;
    private boolean isPanning = false;
    private boolean isDraggingViewRect = false;

    public RoomViewport() {
        this.batch = new SpriteBatch();

        // 1x1 white pixel texture for zero-allocation debug drawing
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        this.pixelTexture = new Texture(pixmap);
        pixmap.dispose();

        this.font = new BitmapFont();
    }

    public void render(RoomEditorState state, RoomRenderer roomRenderer, Map<String, Tileset> tilesets) {
        ImVec2 avail = ImGui.getContentRegionAvail();
        int width = Math.max(1, (int) avail.x);
        int height = Math.max(1, (int) avail.y);

        if (fbo == null || fbo.getWidth() != width || fbo.getHeight() != height) {
            if (fbo != null) fbo.dispose();
            fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        }

        RoomDocument doc = state.getDocument();
        int zoom = state.getZoom();
        float camX = state.getCamX();
        float camY = state.getCamY();

        // Render scene to FBO
        fbo.begin();
        Gdx.gl.glClearColor(0.08f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float viewW = (float) width / zoom;
        float viewH = (float) height / zoom;

        batch.getProjectionMatrix().setToOrtho2D(camX, camY, viewW, viewH);
        batch.begin();

        Room runtimeRoom = doc.toRoom();

        // 1. Render room tile layers
        for (RoomLayerType layer : RENDER_ORDER) {
            if (!doc.isLayerVisible(layer)) continue;

            if (state.isDimOtherLayers() && layer != state.getActiveLayer()) {
                batch.setColor(1f, 1f, 1f, 0.35f);
            } else {
                batch.setColor(1f, 1f, 1f, 1f);
            }
            roomRenderer.renderLayer(batch, runtimeRoom, layer, tilesets, (int) camX, (int) camY, (int) viewW, (int) viewH, TILE_SIZE);
        }
        batch.setColor(1f, 1f, 1f, 1f);

        // 2. Overlays
        if (state.isShowCollision()) {
            renderCollisionOverlay(doc, tilesets, camX, camY, viewW, viewH);
        }

        if (state.isShowGrid()) {
            renderGridOverlay(doc, camX, camY, viewW, viewH);
        }

        renderSpawnPoints(doc);
        renderExits(doc);
        renderEntities(state, doc);
        renderToolPreview(state);

        if (state.isShowViewRect()) {
            renderViewRect(state);
        }

        batch.end();
        fbo.end();

        // Display FBO in ImGui panel (UV inverted on Y for OpenGL)
        ImVec2 screenPos = ImGui.getCursorScreenPos();
        ImGui.image(fbo.getColorBufferTexture().getTextureObjectHandle(), width, height, 0, 1, 1, 0);

        // Handle viewport mouse input (pan, zoom, tools)
        handleInput(state, screenPos.x, screenPos.y, width, height);
    }

    private void renderGridOverlay(RoomDocument doc, float camX, float camY, float viewW, float viewH) {
        batch.setColor(0.4f, 0.4f, 0.5f, 0.25f);
        int roomW = doc.getWidth() * TILE_SIZE;
        int roomH = doc.getHeight() * TILE_SIZE;

        int minX = Math.max(0, (int) (camX / TILE_SIZE) * TILE_SIZE);
        int maxX = Math.min(roomW, (int) ((camX + viewW) / TILE_SIZE + 1) * TILE_SIZE);
        int minY = Math.max(0, (int) (camY / TILE_SIZE) * TILE_SIZE);
        int maxY = Math.min(roomH, (int) ((camY + viewH) / TILE_SIZE + 1) * TILE_SIZE);

        for (int x = minX; x <= maxX; x += TILE_SIZE) {
            batch.draw(pixelTexture, x, 0, 1, roomH);
        }
        for (int y = minY; y <= maxY; y += TILE_SIZE) {
            batch.draw(pixelTexture, 0, y, roomW, 1);
        }
        batch.setColor(Color.WHITE);
    }

    private void renderCollisionOverlay(RoomDocument doc, Map<String, Tileset> tilesets, float camX, float camY, float viewW, float viewH) {
        RoomLayer layer = doc.toRoom().getLayer(RoomLayerType.MAIN);
        if (layer == null) return;

        int minTileX = Math.max(0, (int) (camX / TILE_SIZE));
        int maxTileX = Math.min(doc.getWidth() - 1, (int) ((camX + viewW) / TILE_SIZE));
        int minTileY = Math.max(0, (int) (camY / TILE_SIZE));
        int maxTileY = Math.min(doc.getHeight() - 1, (int) ((camY + viewH) / TILE_SIZE));

        for (int y = minTileY; y <= maxTileY; y++) {
            for (int x = minTileX; x <= maxTileX; x++) {
                String token = layer.getToken(x, y);
                if (token == null || token.equals(RoomLayer.EMPTY_TOKEN)) continue;

                TileShape shape = resolveShape(doc, tilesets, token);
                int px = x * TILE_SIZE;
                int py = y * TILE_SIZE;

                switch (shape) {
                    case SOLID -> {
                        batch.setColor(0.1f, 0.6f, 1.0f, 0.35f);
                        batch.draw(pixelTexture, px + 1, py + 1, TILE_SIZE - 2, TILE_SIZE - 2);
                    }
                    case ONE_WAY -> {
                        batch.setColor(0.2f, 1.0f, 0.2f, 0.7f);
                        batch.draw(pixelTexture, px, py + TILE_SIZE - 3, TILE_SIZE, 3);
                    }
                    case SLOPE_45_UP_RIGHT -> {
                        batch.setColor(1.0f, 0.8f, 0.2f, 0.4f);
                        // Diagonal stepping
                        for (int sx = 0; sx < TILE_SIZE; sx++) {
                            batch.draw(pixelTexture, px + sx, py, 1, sx + 1);
                        }
                    }
                    case SLOPE_45_UP_LEFT -> {
                        batch.setColor(1.0f, 0.8f, 0.2f, 0.4f);
                        for (int sx = 0; sx < TILE_SIZE; sx++) {
                            batch.draw(pixelTexture, px + sx, py, 1, TILE_SIZE - sx);
                        }
                    }
                    case EMPTY -> {}
                }
            }
        }
        batch.setColor(Color.WHITE);
    }

    private TileShape resolveShape(RoomDocument doc, Map<String, Tileset> tilesets, String token) {
        String[] parts = token.split(":", 2);
        if (parts.length != 2) return TileShape.SOLID;
        String tilesetId = doc.getTilesetAliases().get(parts[0]);
        if (tilesetId == null) return TileShape.SOLID;
        Tileset ts = tilesets.get(tilesetId);
        if (ts == null) return TileShape.SOLID;
        TileDef def = ts.tiles().get(parts[1]);
        return def != null ? def.shape() : TileShape.SOLID;
    }

    private void renderEntities(RoomEditorState state, RoomDocument doc) {
        String selectedId = state.getSelectionModel().getSelectedInstanceId();

        for (com.makeasaying.engine.world.RoomEntityData ed : doc.getEntities()) {
            com.makeasaying.engine.entity.EntityDefinition def = state.getEntityDefs().get(ed.definition());
            com.makeasaying.editor.map.model.SelectionModel.EntityHitBox hitBox =
                com.makeasaying.editor.map.model.SelectionModel.computeHitBox(ed, def);

            boolean isSelected = ed.instanceId().equals(selectedId);

            // Fill color
            if (isSelected) {
                batch.setColor(1.0f, 0.9f, 0.2f, 0.6f);
            } else {
                batch.setColor(0.27f, 0.53f, 1.0f, 0.5f);
            }
            batch.draw(pixelTexture, hitBox.left(), hitBox.bottom(), hitBox.width(), hitBox.height());

            // 1px outline
            batch.setColor(isSelected ? Color.YELLOW : Color.CYAN);
            batch.draw(pixelTexture, hitBox.left(), hitBox.bottom(), hitBox.width(), 1);
            batch.draw(pixelTexture, hitBox.left(), hitBox.bottom() + hitBox.height() - 1, hitBox.width(), 1);
            batch.draw(pixelTexture, hitBox.left(), hitBox.bottom(), 1, hitBox.height());
            batch.draw(pixelTexture, hitBox.left() + hitBox.width() - 1, hitBox.bottom(), 1, hitBox.height());

            // Label
            batch.setColor(Color.WHITE);
            font.setColor(isSelected ? Color.YELLOW : Color.WHITE);
            font.draw(batch, ed.instanceId(), hitBox.left(), hitBox.bottom() + hitBox.height() + 12);

            // If selected platform, draw trajectory line and endpoint handle
            if (isSelected) {
                com.makeasaying.editor.map.model.GizmoModel.PlatformGizmo gizmo =
                    com.makeasaying.editor.map.model.GizmoModel.computePlatformGizmo(ed, def);
                if (gizmo != null) {
                    batch.setColor(0.3f, 1.0f, 0.3f, 0.8f);
                    drawLine(batch, gizmo.startX(), gizmo.startY(), gizmo.endX(), gizmo.endY());

                    batch.setColor(Color.GREEN);
                    batch.draw(pixelTexture, gizmo.endX() - 4, gizmo.endY() - 4, 8, 8);
                    batch.setColor(Color.WHITE);
                    font.setColor(Color.GREEN);
                    font.draw(batch, "End (" + gizmo.travelX() + "," + gizmo.travelY() + ")", gizmo.endX() + 6, gizmo.endY() + 6);
                }
            }
        }
        batch.setColor(Color.WHITE);
    }

    private void drawLine(SpriteBatch batch, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist <= 0.001f) return;
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        batch.draw(pixelTexture, x1, y1, 0, 0, dist, 1, 1, 1, angle, 0, 0, 1, 1, false, false);
    }

    private void renderSpawnPoints(RoomDocument doc) {
        for (SpawnPoint sp : doc.getSpawnPoints()) {
            batch.setColor(0.2f, 1.0f, 0.3f, 0.8f);
            batch.draw(pixelTexture, sp.x() - 4, sp.y() - 4, 8, 8);
            batch.setColor(Color.WHITE);
            font.setColor(0.2f, 1.0f, 0.3f, 1.0f);
            font.draw(batch, sp.id(), sp.x() + 6, sp.y() + 6);
        }
    }

    private void renderExits(RoomDocument doc) {
        for (RoomExit ex : doc.getExits()) {
            batch.setColor(1.0f, 0.5f, 0.1f, 0.4f);
            batch.draw(pixelTexture, ex.x(), ex.y(), ex.width(), ex.height());
            batch.setColor(1.0f, 0.5f, 0.1f, 0.9f);
            // 1px outline
            batch.draw(pixelTexture, ex.x(), ex.y(), ex.width(), 1);
            batch.draw(pixelTexture, ex.x(), ex.y() + ex.height() - 1, ex.width(), 1);
            batch.draw(pixelTexture, ex.x(), ex.y(), 1, ex.height());
            batch.draw(pixelTexture, ex.x() + ex.width() - 1, ex.y(), 1, ex.height());

            batch.setColor(Color.WHITE);
            font.setColor(1.0f, 0.5f, 0.1f, 1.0f);
            font.draw(batch, ex.id() + " -> " + ex.targetRoom(), ex.x() + 2, ex.y() + ex.height() - 2);
        }
    }

    private void renderToolPreview(RoomEditorState state) {
        List<GeometryRasterizer.Point> preview = state.getActiveTool().getPreviewPoints();
        if (preview.isEmpty()) return;

        batch.setColor(1f, 1f, 0.2f, 0.6f);
        for (GeometryRasterizer.Point p : preview) {
            batch.draw(pixelTexture, p.x() * TILE_SIZE, p.y() * TILE_SIZE, TILE_SIZE, TILE_SIZE);
        }
        batch.setColor(Color.WHITE);
    }

    private void renderViewRect(RoomEditorState state) {
        int vx = state.getViewRectX();
        int vy = state.getViewRectY();

        batch.setColor(0.9f, 0.9f, 0.2f, 0.75f);
        // 320x180 1px frame
        batch.draw(pixelTexture, vx, vy, GAME_VIEW_W, 1);
        batch.draw(pixelTexture, vx, vy + GAME_VIEW_H - 1, GAME_VIEW_W, 1);
        batch.draw(pixelTexture, vx, vy, 1, GAME_VIEW_H);
        batch.draw(pixelTexture, vx + GAME_VIEW_W - 1, vy, 1, GAME_VIEW_H);

        batch.setColor(Color.WHITE);
        font.setColor(0.9f, 0.9f, 0.2f, 0.9f);
        font.draw(batch, "320x180 View", vx + 4, vy + GAME_VIEW_H - 4);
    }

    private void handleInput(RoomEditorState state, float panelX, float panelY, int panelW, int panelH) {
        if (!ImGui.isWindowHovered() && !ImGui.isWindowFocused()) {
            isPanning = false;
            return;
        }

        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();

        // 1. Pan handling (Middle-drag or Space-drag)
        boolean spaceDown = ImGui.isKeyDown(32); // Space
        boolean middleDown = ImGui.isMouseDown(2);
        boolean panButtonDown = middleDown || (spaceDown && ImGui.isMouseDown(0));

        if (panButtonDown) {
            if (!isPanning) {
                isPanning = true;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
            } else {
                float dx = (mouseX - lastMouseX) / state.getZoom();
                float dy = (mouseY - lastMouseY) / state.getZoom();
                // Screen Y is down, Ortho Y is up
                state.setCamX(state.getCamX() - dx);
                state.setCamY(state.getCamY() + dy);
                lastMouseX = mouseX;
                lastMouseY = mouseY;
            }
            return;
        } else {
            isPanning = false;
        }

        // 2. Zoom handling (Mouse wheel centered around cursor)
        float wheel = ImGui.getIO().getMouseWheel();
        if (wheel != 0f) {
            int oldZoom = state.getZoom();
            int newZoom = Math.clamp(oldZoom + (wheel > 0 ? 1 : -1), 1, 8);
            if (newZoom != oldZoom) {
                float normX = (mouseX - panelX) / panelW;
                float normY = (mouseY - panelY) / panelH;

                float oldViewW = (float) panelW / oldZoom;
                float oldViewH = (float) panelH / oldZoom;
                float newViewW = (float) panelW / newZoom;
                float newViewH = (float) panelH / newZoom;

                float worldCursorX = state.getCamX() + normX * oldViewW;
                float worldCursorY = state.getCamY() + (1f - normY) * oldViewH;

                state.setCamX(worldCursorX - normX * newViewW);
                state.setCamY(worldCursorY - (1f - normY) * newViewH);
                state.setZoom(newZoom);
            }
        }

        // 3. Tool interaction
        float localX = mouseX - panelX;
        float localY = mouseY - panelY;
        if (localX < 0 || localX >= panelW || localY < 0 || localY >= panelH) return;

        float worldX = state.getCamX() + localX / state.getZoom();
        float worldY = state.getCamY() + ((panelH - localY) / state.getZoom());
        int tileX = (int) Math.floor(worldX / TILE_SIZE);
        int tileY = (int) Math.floor(worldY / TILE_SIZE);

        if (ImGui.isMouseClicked(0)) {
            state.getActiveTool().onMouseDown(
                state.getDocument(),
                state.getActiveLayer(),
                state.getActiveTileToken(),
                tileX,
                tileY
            );
        } else if (ImGui.isMouseDragging(0)) {
            state.getActiveTool().onMouseDrag(
                state.getDocument(),
                state.getActiveLayer(),
                state.getActiveTileToken(),
                tileX,
                tileY
            );
        } else if (ImGui.isMouseReleased(0)) {
            state.getActiveTool().onMouseUp(
                state.getDocument(),
                state.getActiveLayer(),
                state.getActiveTileToken(),
                tileX,
                tileY
            );
        }
    }

    @Override
    public void dispose() {
        if (fbo != null) fbo.dispose();
        if (batch != null) batch.dispose();
        if (pixelTexture != null) pixelTexture.dispose();
        if (font != null) font.dispose();
    }
}
