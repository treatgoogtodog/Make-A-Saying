package com.makeasaying.editor.map.commands;

import com.makeasaying.editor.core.command.EditorCommand;
import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.engine.world.SpawnPoint;

public final class SpawnPointCommands {
    private SpawnPointCommands() {}

    public static final class AddSpawnPointCommand implements EditorCommand {
        private final RoomDocument document;
        private final SpawnPoint spawn;

        public AddSpawnPointCommand(RoomDocument document, SpawnPoint spawn) {
            this.document = document;
            this.spawn = spawn;
        }

        @Override public String getName() { return "Add Spawn Point " + spawn.id(); }
        @Override public void apply() { document.getSpawnPoints().add(spawn); }
        @Override public void revert() { document.getSpawnPoints().remove(spawn); }
    }

    public static final class RemoveSpawnPointCommand implements EditorCommand {
        private final RoomDocument document;
        private final SpawnPoint spawn;
        private final int index;

        public RemoveSpawnPointCommand(RoomDocument document, SpawnPoint spawn) {
            this.document = document;
            this.spawn = spawn;
            this.index = document.getSpawnPoints().indexOf(spawn);
        }

        @Override public String getName() { return "Remove Spawn Point " + spawn.id(); }
        @Override public void apply() { document.getSpawnPoints().remove(spawn); }
        @Override
        public void revert() {
            if (index >= 0 && index <= document.getSpawnPoints().size()) {
                document.getSpawnPoints().add(index, spawn);
            } else {
                document.getSpawnPoints().add(spawn);
            }
        }
    }

    public static final class MoveSpawnPointCommand implements EditorCommand {
        private final RoomDocument document;
        private final String spawnId;
        private final int oldX;
        private final int oldY;
        private final int newX;
        private final int newY;

        public MoveSpawnPointCommand(RoomDocument document, String spawnId, int oldX, int oldY, int newX, int newY) {
            this.document = document;
            this.spawnId = spawnId;
            this.oldX = oldX;
            this.oldY = oldY;
            this.newX = newX;
            this.newY = newY;
        }

        @Override public String getName() { return "Move Spawn Point " + spawnId; }

        @Override
        public void apply() {
            setSpawnCoords(newX, newY);
        }

        @Override
        public void revert() {
            setSpawnCoords(oldX, oldY);
        }

        private void setSpawnCoords(int x, int y) {
            for (int i = 0; i < document.getSpawnPoints().size(); i++) {
                SpawnPoint sp = document.getSpawnPoints().get(i);
                if (sp.id().equals(spawnId)) {
                    document.getSpawnPoints().set(i, new SpawnPoint(spawnId, x, y));
                    break;
                }
            }
        }
    }
}
