package com.makeasaying.editor.core.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Composite editor command grouping multiple atomic sub-commands into exactly one undo/redo step.
 */
public final class CompoundCommand implements EditorCommand {
    private final String name;
    private final List<EditorCommand> commands = new ArrayList<>();

    public CompoundCommand(String name) {
        this.name = name != null ? name : "Compound Command";
    }

    public void add(EditorCommand command) {
        if (command != null) {
            commands.add(command);
        }
    }

    public List<EditorCommand> getCommands() {
        return Collections.unmodifiableList(commands);
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void apply() {
        for (int i = 0; i < commands.size(); i++) {
            commands.get(i).apply();
        }
    }

    @Override
    public void revert() {
        for (int i = commands.size() - 1; i >= 0; i--) {
            commands.get(i).revert();
        }
    }
}
