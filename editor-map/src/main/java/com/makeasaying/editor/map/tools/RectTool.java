package com.makeasaying.editor.map.tools;

import com.makeasaying.editor.core.raster.GeometryRasterizer;
import com.makeasaying.editor.map.commands.PaintBatchCommand;
import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.engine.world.RoomLayerType;

import java.util.Collections;
import java.util.List;

/**
 * Rectangle painting tool supporting outline and filled shapes.
 */
public final class RectTool implements EditorTool {
    private final boolean filled;
    private int startX = -1;
    private int startY = -1;
    private int currentX = -1;
    private int currentY = -1;
    private boolean dragging = false;

    public RectTool(boolean filled) {
        this.filled = filled;
    }

    public RectTool() {
        this(false);
    }

    public boolean isFilled() {
        return filled;
    }

    @Override
    public String getName() {
        return filled ? "Filled Rectangle" : "Rectangle Outline";
    }

    @Override
    public void onMouseDown(RoomDocument doc, RoomLayerType layer, String activeToken, int tileX, int tileY) {
        startX = tileX;
        startY = tileY;
        currentX = tileX;
        currentY = tileY;
        dragging = true;
    }

    @Override
    public void onMouseDrag(RoomDocument doc, RoomLayerType layer, String activeToken, int tileX, int tileY) {
        if (!dragging) return;
        currentX = tileX;
        currentY = tileY;
    }

    @Override
    public void onMouseUp(RoomDocument doc, RoomLayerType layer, String activeToken, int tileX, int tileY) {
        if (!dragging) return;
        currentX = tileX;
        currentY = tileY;
        List<GeometryRasterizer.Point> points = filled
            ? GeometryRasterizer.rasterizeRectFilled(startX, startY, currentX, currentY)
            : GeometryRasterizer.rasterizeRectOutline(startX, startY, currentX, currentY);

        if (!points.isEmpty()) {
            PaintBatchCommand cmd = PaintBatchCommand.create(
                doc,
                layer,
                getName(),
                points,
                activeToken
            );
            doc.getUndoStack().execute(cmd);
        }
        onCancel();
    }

    @Override
    public void onCancel() {
        dragging = false;
        startX = -1;
        startY = -1;
        currentX = -1;
        currentY = -1;
    }

    @Override
    public List<GeometryRasterizer.Point> getPreviewPoints() {
        if (!dragging) return Collections.emptyList();
        return filled
            ? GeometryRasterizer.rasterizeRectFilled(startX, startY, currentX, currentY)
            : GeometryRasterizer.rasterizeRectOutline(startX, startY, currentX, currentY);
    }
}
