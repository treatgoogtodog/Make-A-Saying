package com.makeasaying.engine.flags;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry holding compiled flag definitions.
 * Created at startup and passed down (no global singleton).
 */
public final class FlagRegistry {
    private final FlagDefinition[] definitions;
    private final Map<String, FlagDefinition> byId;

    public FlagRegistry(List<FlagDefinition> flags) {
        if (flags == null) {
            this.definitions = new FlagDefinition[0];
            this.byId = Collections.emptyMap();
            return;
        }

        this.definitions = new FlagDefinition[flags.size()];
        this.byId = new HashMap<>(flags.size());

        for (int i = 0; i < flags.size(); i++) {
            FlagDefinition def = flags.get(i);
            this.definitions[i] = def;
            this.byId.put(def.id(), def);
        }
    }

    public int size() {
        return definitions.length;
    }

    public FlagDefinition get(int index) {
        if (index < 0 || index >= definitions.length) {
            return null;
        }
        return definitions[index];
    }

    public FlagDefinition get(String id) {
        if (id == null) return null;
        return byId.get(id);
    }

    public int indexOf(String id) {
        FlagDefinition def = get(id);
        return def != null ? def.index() : -1;
    }

    public boolean contains(String id) {
        return byId.containsKey(id);
    }

    public List<FlagDefinition> getAll() {
        return List.of(definitions);
    }
}
