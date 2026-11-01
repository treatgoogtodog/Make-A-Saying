package com.makeasaying.editor.map.tools;

import com.makeasaying.editor.core.raster.GeometryRasterizer;
import com.makeasaying.editor.map.commands.PaintBatchCommand;
import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.engine.world.RoomLayer;
import com.makeasaying.engine.world.RoomLayerType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Eraser tool that replaces tiles with EMPTY_TOKEN ("..").
 */
public final class EraserTool implements EditorTool {
    private final Set<GeometryRasterizer.Point> strokePoints = new LinkedHashSet<>();
    private int lastX = -1;
    private int lastY = -1;
    private boolean active = false;

    @Override
    public String getName() {
        return "Eraser";
    }

    @Override
    public void onMouseDown(RoomDocument doc, RoomLayerType layer, String activeToken, int tileX, int tileY) {
        strokePoints.clear();
        lastX = tileX;
        lastY = tileY;
        strokePoints.add(new GeometryRasterizer.Point(tileX, tileY));
        active = true;
    }

    @Override
    public void onMouseDrag(RoomDocument doc, RoomLayerType layer, String activeToken, int tileX, int tileY) {
        if (!active) return;
        List<GeometryRasterizer.Point> interpolated = GeometryRasterizer.rasterizeLine(lastX, lastY, tileX, tileY);
        strokePoints.addAll(interpolated);
        lastX = tileX;
        lastY = tileY;
    }

    @Override
    public void onMouseUp(RoomDocument doc, RoomLayerType layer, String activeToken, int tileX, int tileY) {
        if (!active) return;
        onMouseDrag(doc, layer, activeToken, tileX, tileY);
        if (!strokePoints.isEmpty()) {
            PaintBatchCommand cmd = PaintBatchCommand.create(
                doc,
                layer,
                "Eraser",
                new ArrayList<>(strokePoints),
                RoomLayer.EMPTY_TOKEN
            );
            doc.getUndoStack().execute(cmd);
        }
        onCancel();
    }

    @Override
    public void onCancel() {
        strokePoints.clear();
        active = false;
        lastX = -1;
        lastY = -1;
    }

    @Override
    public List<GeometryRasterizer.Point> getPreviewPoints() {
        return List.copyOf(strokePoints);
    }
}
