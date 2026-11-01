package com.makeasaying.engine.input;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry holding loaded action definitions.
 */
public final class ActionRegistry {
    private final Map<String, ActionDefinition> actions = new HashMap<>();

    public ActionRegistry() {}

    public void register(ActionDefinition action) {
        if (action == null || action.id() == null) {
            throw new IllegalArgumentException("Action definition and ID cannot be null");
        }
        actions.put(action.id(), action);
    }

    public ActionDefinition get(String id) {
        return actions.get(id);
    }

    public boolean contains(String id) {
        return actions.containsKey(id);
    }

    public boolean isButton(String id) {
        ActionDefinition def = actions.get(id);
        return def != null && def.type() == ActionDefinition.ActionType.BUTTON;
    }

    public boolean isAxis(String id) {
        ActionDefinition def = actions.get(id);
        return def != null && def.type() == ActionDefinition.ActionType.AXIS;
    }

    public Collection<ActionDefinition> getAll() {
        return Collections.unmodifiableCollection(actions.values());
    }
}
