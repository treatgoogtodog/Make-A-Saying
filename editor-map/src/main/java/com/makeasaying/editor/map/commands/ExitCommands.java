package com.makeasaying.editor.map.commands;

import com.makeasaying.editor.core.command.EditorCommand;
import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.engine.world.RoomExit;

public final class ExitCommands {
    private ExitCommands() {}

    public static final class AddExitCommand implements EditorCommand {
        private final RoomDocument document;
        private final RoomExit exit;

        public AddExitCommand(RoomDocument document, RoomExit exit) {
            this.document = document;
            this.exit = exit;
        }

        @Override public String getName() { return "Add Exit " + exit.id(); }
        @Override public void apply() { document.getExits().add(exit); }
        @Override public void revert() { document.getExits().remove(exit); }
    }

    public static final class RemoveExitCommand implements EditorCommand {
        private final RoomDocument document;
        private final RoomExit exit;
        private final int index;

        public RemoveExitCommand(RoomDocument document, RoomExit exit) {
            this.document = document;
            this.exit = exit;
            this.index = document.getExits().indexOf(exit);
        }

        @Override public String getName() { return "Remove Exit " + exit.id(); }
        @Override public void apply() { document.getExits().remove(exit); }
        @Override
        public void revert() {
            if (index >= 0 && index <= document.getExits().size()) {
                document.getExits().add(index, exit);
            } else {
                document.getExits().add(exit);
            }
        }
    }

    public static final class UpdateExitCommand implements EditorCommand {
        private final RoomDocument document;
        private final RoomExit oldExit;
        private final RoomExit newExit;

        public UpdateExitCommand(RoomDocument document, RoomExit oldExit, RoomExit newExit) {
            this.document = document;
            this.oldExit = oldExit;
            this.newExit = newExit;
        }

        @Override public String getName() { return "Update Exit " + newExit.id(); }

        @Override
        public void apply() {
            replace(oldExit.id(), newExit);
        }

        @Override
        public void revert() {
            replace(newExit.id(), oldExit);
        }

        private void replace(String targetId, RoomExit replacement) {
            for (int i = 0; i < document.getExits().size(); i++) {
                if (document.getExits().get(i).id().equals(targetId)) {
                    document.getExits().set(i, replacement);
                    break;
                }
            }
        }
    }
}
