package com.makeasaying.editor.map.commands;

import com.makeasaying.editor.core.command.EditorCommand;
import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.engine.world.RoomLayerType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Command for batch tile modifications on a specific room layer (strokes, shapes, flood fills).
 * Reverts to previous tokens on undo.
 * Does nothing if target layer is locked.
 */
public final class PaintBatchCommand implements EditorCommand {
    public record TileChange(int x, int y, String oldToken, String newToken) {}

    private final RoomDocument document;
    private final RoomLayerType layerType;
    private final String name;
    private final List<TileChange> changes;

    public PaintBatchCommand(RoomDocument document, RoomLayerType layerType, String name, List<TileChange> changes) {
        this.document = document;
        this.layerType = layerType;
        this.name = name != null ? name : "Paint Tiles";
        this.changes = changes != null ? List.copyOf(changes) : Collections.emptyList();
    }

    public static PaintBatchCommand create(
        RoomDocument document,
        RoomLayerType layerType,
        String name,
        List<com.makeasaying.editor.core.raster.GeometryRasterizer.Point> points,
        String newToken
    ) {
        if (document.isLayerLocked(layerType)) {
            return new PaintBatchCommand(document, layerType, name, Collections.emptyList());
        }

        List<TileChange> changes = new ArrayList<>();
        for (com.makeasaying.editor.core.raster.GeometryRasterizer.Point p : points) {
            String oldToken = document.getToken(layerType, p.x(), p.y());
            if (!oldToken.equals(newToken)) {
                changes.add(new TileChange(p.x(), p.y(), oldToken, newToken));
            }
        }
        return new PaintBatchCommand(document, layerType, name, changes);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void apply() {
        if (document.isLayerLocked(layerType)) return;
        for (TileChange c : changes) {
            document.setTokenInternal(layerType, c.x(), c.y(), c.newToken());
        }
    }

    @Override
    public void revert() {
        if (document.isLayerLocked(layerType)) return;
        for (TileChange c : changes) {
            document.setTokenInternal(layerType, c.x(), c.y(), c.oldToken());
        }
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }
}
