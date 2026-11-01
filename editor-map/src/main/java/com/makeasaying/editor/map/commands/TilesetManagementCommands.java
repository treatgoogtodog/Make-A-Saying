package com.makeasaying.editor.map.commands;

import com.makeasaying.editor.core.command.EditorCommand;
import com.makeasaying.editor.map.document.RoomDocument;

/**
 * Commands for adding and removing tileset mappings in the room inspector.
 */
public final class TilesetManagementCommands {
    private TilesetManagementCommands() {}

    public static final class AddTilesetCommand implements EditorCommand {
        private final RoomDocument document;
        private final String alias;
        private final String tilesetId;

        public AddTilesetCommand(RoomDocument document, String alias, String tilesetId) {
            this.document = document;
            this.alias = alias;
            this.tilesetId = tilesetId;
        }

        @Override public String getName() { return "Add Tileset " + alias + ":" + tilesetId; }

        @Override
        public void apply() {
            document.getTilesetAliases().put(alias, tilesetId);
        }

        @Override
        public void revert() {
            document.getTilesetAliases().remove(alias);
        }
    }

    public static final class RemoveTilesetCommand implements EditorCommand {
        private final RoomDocument document;
        private final String alias;
        private final String tilesetId;

        public RemoveTilesetCommand(RoomDocument document, String alias) {
            this.document = document;
            this.alias = alias;
            this.tilesetId = document.getTilesetAliases().get(alias);

            int usageCount = document.countTokenUsages(alias + ":");
            if (usageCount > 0) {
                throw new IllegalStateException(String.format(
                    "Cannot remove tileset '%s': %d tiles currently in use in this room", alias, usageCount));
            }
        }

        @Override public String getName() { return "Remove Tileset " + alias; }

        @Override
        public void apply() {
            document.getTilesetAliases().remove(alias);
        }

        @Override
        public void revert() {
            document.getTilesetAliases().put(alias, tilesetId);
        }
    }
}
