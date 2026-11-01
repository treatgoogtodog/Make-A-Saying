package com.makeasaying.editor.map.tools;

import com.makeasaying.editor.core.raster.GeometryRasterizer;
import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.engine.world.RoomLayerType;

import java.util.List;

/**
 * Interface for viewport tile interaction tools per M6a brief.
 * Tools are pure logic, testable headlessly without GL/UI.
 */
public interface EditorTool {
    String getName();

    void onMouseDown(RoomDocument doc, RoomLayerType layer, String activeToken, int tileX, int tileY);

    void onMouseDrag(RoomDocument doc, RoomLayerType layer, String activeToken, int tileX, int tileY);

    void onMouseUp(RoomDocument doc, RoomLayerType layer, String activeToken, int tileX, int tileY);

    void onCancel();

    List<GeometryRasterizer.Point> getPreviewPoints();
}
