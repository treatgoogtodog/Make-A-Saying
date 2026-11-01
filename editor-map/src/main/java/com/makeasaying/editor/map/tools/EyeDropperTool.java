package com.makeasaying.editor.map.tools;

import com.makeasaying.editor.core.raster.GeometryRasterizer;
import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.engine.world.RoomLayer;
import com.makeasaying.engine.world.RoomLayerType;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tile picker / dropper tool that samples the token at the clicked coordinate
 * and notifies a callback to set the active palette tile.
 */
public final class EyeDropperTool implements EditorTool {
    private final Consumer<String> onTilePicked;

    public EyeDropperTool(Consumer<String> onTilePicked) {
        this.onTilePicked = onTilePicked;
    }

    @Override
    public String getName() {
        return "Eye Dropper";
    }

    @Override
    public void onMouseDown(RoomDocument doc, RoomLayerType layer, String activeToken, int tileX, int tileY) {
        sample(doc, layer, tileX, tileY);
    }

    @Override
    public void onMouseDrag(RoomDocument doc, RoomLayerType layer, String activeToken, int tileX, int tileY) {
        sample(doc, layer, tileX, tileY);
    }

    @Override
    public void onMouseUp(RoomDocument doc, RoomLayerType layer, String activeToken, int tileX, int tileY) {
        sample(doc, layer, tileX, tileY);
    }

    private void sample(RoomDocument doc, RoomLayerType layer, int tileX, int tileY) {
        if (tileX < 0 || tileX >= doc.getWidth() || tileY < 0 || tileY >= doc.getHeight()) return;

        // Sample from specified layer if non-empty; if empty, look from FOREGROUND down to FAR_BACKGROUND
        String token = doc.getToken(layer, tileX, tileY);
        if (token.equals(RoomLayer.EMPTY_TOKEN)) {
            for (RoomLayerType type : new RoomLayerType[] {
                RoomLayerType.FOREGROUND,
                RoomLayerType.DECORATION,
                RoomLayerType.MAIN,
                RoomLayerType.NEAR_BACKGROUND,
                RoomLayerType.FAR_BACKGROUND
            }) {
                String candidate = doc.getToken(type, tileX, tileY);
                if (!candidate.equals(RoomLayer.EMPTY_TOKEN)) {
                    token = candidate;
                    break;
                }
            }
        }

        if (onTilePicked != null && !token.equals(RoomLayer.EMPTY_TOKEN)) {
            onTilePicked.accept(token);
        }
    }

    @Override
    public void onCancel() {}

    @Override
    public List<GeometryRasterizer.Point> getPreviewPoints() {
        return Collections.emptyList();
    }
}
