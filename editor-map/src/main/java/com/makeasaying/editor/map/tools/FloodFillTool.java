package com.makeasaying.editor.map.tools;

import com.makeasaying.editor.core.raster.GeometryRasterizer;
import com.makeasaying.editor.map.commands.PaintBatchCommand;
import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.engine.world.RoomLayerType;

import java.util.Collections;
import java.util.List;

/**
 * 4-way flood fill tool bounded by room dimensions and configured max area cap.
 */
public final class FloodFillTool implements EditorTool {
    private final int maxCap;

    public FloodFillTool(int maxCap) {
        this.maxCap = maxCap > 0 ? maxCap : GeometryRasterizer.DEFAULT_FLOOD_FILL_CAP;
    }

    public FloodFillTool() {
        this(GeometryRasterizer.DEFAULT_FLOOD_FILL_CAP);
    }

    @Override
    public String getName() {
        return "Flood Fill";
    }

    @Override
    public void onMouseDown(RoomDocument doc, RoomLayerType layer, String activeToken, int tileX, int tileY) {
        if (tileX < 0 || tileX >= doc.getWidth() || tileY < 0 || tileY >= doc.getHeight()) return;

        String targetToken = doc.getToken(layer, tileX, tileY);
        if (targetToken.equals(activeToken)) {
            return; // No-op if target token already equals active token
        }

        try {
            List<GeometryRasterizer.Point> filledPoints = GeometryRasterizer.floodFill(
                tileX,
                tileY,
                doc.getWidth(),
                doc.getHeight(),
                (x, y) -> doc.getToken(layer, x, y).equals(targetToken),
                maxCap
            );

            if (!filledPoints.isEmpty()) {
                PaintBatchCommand cmd = PaintBatchCommand.create(
                    doc,
                    layer,
                    "Flood Fill",
                    filledPoints,
                    activeToken
                );
                doc.getUndoStack().execute(cmd);
            }
        } catch (IllegalStateException ignored) {
            // Cap exceeded - do not corrupt room, reject fill gracefully
        }
    }

    @Override
    public void onMouseDrag(RoomDocument doc, RoomLayerType layer, String activeToken, int tileX, int tileY) {}

    @Override
    public void onMouseUp(RoomDocument doc, RoomLayerType layer, String activeToken, int tileX, int tileY) {}

    @Override
    public void onCancel() {}

    @Override
    public List<GeometryRasterizer.Point> getPreviewPoints() {
        return Collections.emptyList();
    }
}
