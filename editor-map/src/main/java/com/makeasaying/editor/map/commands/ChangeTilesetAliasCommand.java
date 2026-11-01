package com.makeasaying.editor.map.commands;

import com.makeasaying.editor.core.command.EditorCommand;
import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.engine.world.RoomLayerType;

import java.util.ArrayList;
import java.util.List;

/**
 * Command renaming a tileset alias and rewriting all referenced tokens across all layers
 * in exactly one undo step per M6a brief.
 */
public final class ChangeTilesetAliasCommand implements EditorCommand {
    private record LayerChange(RoomLayerType layerType, int index, String oldToken, String newToken) {}

    private final RoomDocument document;
    private final String oldAlias;
    private final String newAlias;
    private final String tilesetId;
    private final List<LayerChange> changes = new ArrayList<>();

    public ChangeTilesetAliasCommand(RoomDocument document, String oldAlias, String newAlias) {
        this.document = document;
        this.oldAlias = oldAlias;
        this.newAlias = newAlias;
        this.tilesetId = document.getTilesetAliases().get(oldAlias);

        String oldPrefix = oldAlias + ":";
        String newPrefix = newAlias + ":";
        int w = document.getWidth();
        int h = document.getHeight();

        for (RoomLayerType type : RoomLayerType.values()) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    String token = document.getToken(type, x, y);
                    if (token != null && token.startsWith(oldPrefix)) {
                        String replaced = newPrefix + token.substring(oldPrefix.length());
                        changes.add(new LayerChange(type, y * w + x, token, replaced));
                    }
                }
            }
        }
    }

    @Override
    public String getName() {
        return "Change Alias: " + oldAlias + " -> " + newAlias;
    }

    @Override
    public void apply() {
        document.getTilesetAliases().remove(oldAlias);
        document.getTilesetAliases().put(newAlias, tilesetId);

        int w = document.getWidth();
        for (LayerChange c : changes) {
            int x = c.index() % w;
            int y = c.index() / w;
            document.setTokenInternal(c.layerType(), x, y, c.newToken());
        }
    }

    @Override
    public void revert() {
        document.getTilesetAliases().remove(newAlias);
        document.getTilesetAliases().put(oldAlias, tilesetId);

        int w = document.getWidth();
        for (LayerChange c : changes) {
            int x = c.index() % w;
            int y = c.index() / w;
            document.setTokenInternal(c.layerType(), x, y, c.oldToken());
        }
    }
}
