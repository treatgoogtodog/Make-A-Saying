package com.makeasaying.editor.map.commands;

import com.makeasaying.editor.core.command.EditorCommand;
import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.engine.world.RoomLayer;
import com.makeasaying.engine.world.RoomLayerType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Command for resizing a room with anchor alignment per M6a brief.
 */
public final class ResizeRoomCommand implements EditorCommand {
    public enum Anchor {
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        TOP_LEFT,
        TOP_RIGHT,
        CENTER
    }

    private final RoomDocument document;
    private final int oldWidth;
    private final int oldHeight;
    private final int newWidth;
    private final int newHeight;
    private final Anchor anchor;

    private final Map<RoomLayerType, String[]> oldLayers = new EnumMap<>(RoomLayerType.class);
    private final Map<RoomLayerType, String[]> newLayers = new EnumMap<>(RoomLayerType.class);

    public ResizeRoomCommand(RoomDocument document, int newWidth, int newHeight, Anchor anchor) {
        if (newWidth <= 0 || newHeight <= 0) throw new IllegalArgumentException("Dimensions must be positive");
        this.document = document;
        this.oldWidth = document.getWidth();
        this.oldHeight = document.getHeight();
        this.newWidth = newWidth;
        this.newHeight = newHeight;
        this.anchor = anchor != null ? anchor : Anchor.BOTTOM_LEFT;

        // Calculate offset based on anchor
        int offsetX = switch (this.anchor) {
            case BOTTOM_LEFT, TOP_LEFT -> 0;
            case BOTTOM_RIGHT, TOP_RIGHT -> newWidth - oldWidth;
            case CENTER -> (newWidth - oldWidth) / 2;
        };

        int offsetY = switch (this.anchor) {
            case BOTTOM_LEFT, BOTTOM_RIGHT -> 0;
            case TOP_LEFT, TOP_RIGHT -> newHeight - oldHeight;
            case CENTER -> (newHeight - oldHeight) / 2;
        };

        for (RoomLayerType type : RoomLayerType.values()) {
            String[] oldTokens = new String[oldWidth * oldHeight];
            for (int y = 0; y < oldHeight; y++) {
                for (int x = 0; x < oldWidth; x++) {
                    oldTokens[y * oldWidth + x] = document.getToken(type, x, y);
                }
            }
            oldLayers.put(type, oldTokens);

            String[] resTokens = new String[newWidth * newHeight];
            for (int i = 0; i < resTokens.length; i++) {
                resTokens[i] = RoomLayer.EMPTY_TOKEN;
            }

            for (int y = 0; y < oldHeight; y++) {
                int targetY = y + offsetY;
                if (targetY < 0 || targetY >= newHeight) continue;
                for (int x = 0; x < oldWidth; x++) {
                    int targetX = x + offsetX;
                    if (targetX < 0 || targetX >= newWidth) continue;
                    resTokens[targetY * newWidth + targetX] = oldTokens[y * oldWidth + x];
                }
            }
            newLayers.put(type, resTokens);
        }
    }

    @Override
    public String getName() {
        return String.format("Resize Room (%dx%d -> %dx%d, %s)", oldWidth, oldHeight, newWidth, newHeight, anchor);
    }

    @Override
    public void apply() {
        document.resizeInternal(newWidth, newHeight, newLayers);
    }

    @Override
    public void revert() {
        document.resizeInternal(oldWidth, oldHeight, oldLayers);
    }
}
