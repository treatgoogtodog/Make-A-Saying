package com.makeasaying.engine.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Key bindings per context per AGENTS.md section 7.4.
 */
public final class ContextBindings {
    // context -> (actionId -> list of keyCodes)
    private final Map<String, Map<String, List<Integer>>> actionToKeys = new HashMap<>();

    // context -> (keyCode -> actionId)
    private final Map<String, Map<Integer, String>> keyToAction = new HashMap<>();

    // Fast non-allocating key code lookup per context
    private final Map<String, String[]> fastLookup = new HashMap<>();

    public ContextBindings() {}

    public void bind(String context, String actionId, int keyCode) {
        actionToKeys.computeIfAbsent(context, k -> new HashMap<>())
            .computeIfAbsent(actionId, a -> new ArrayList<>())
            .add(keyCode);

        keyToAction.computeIfAbsent(context, k -> new HashMap<>())
            .put(keyCode, actionId);

        String[] fast = fastLookup.computeIfAbsent(context, c -> new String[512]);
        if (keyCode >= 0 && keyCode < 512) {
            fast[keyCode] = actionId;
        }
    }

    public String getActionForKey(String context, int keyCode) {
        String[] fast = fastLookup.get(context);
        if (fast != null && keyCode >= 0 && keyCode < 512) {
            return fast[keyCode];
        }
        return null;
    }

    public List<Integer> getKeysForAction(String context, String actionId) {
        Map<String, List<Integer>> map = actionToKeys.get(context);
        if (map != null && map.containsKey(actionId)) {
            return Collections.unmodifiableList(map.get(actionId));
        }
        return Collections.emptyList();
    }

    public boolean hasBindingForAction(String context, String actionId) {
        Map<String, List<Integer>> map = actionToKeys.get(context);
        return map != null && map.containsKey(actionId) && !map.get(actionId).isEmpty();
    }

    public Map<String, Map<String, List<Integer>>> getAllBindings() {
        return Collections.unmodifiableMap(actionToKeys);
    }
}
