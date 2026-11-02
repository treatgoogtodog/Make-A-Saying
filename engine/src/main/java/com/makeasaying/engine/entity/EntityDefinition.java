package com.makeasaying.engine.entity;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Runtime representation of an entity definition per AGENTS.md M6b brief.
 * Stored as data in content/entities/<id>.json.
 */
public record EntityDefinition(
    int version,
    String id,
    List<String> tags,
    String debugColor,
    boolean placeable,
    Map<String, Map<String, Object>> components
) {
    public EntityDefinition {
        tags = (tags != null) ? Collections.unmodifiableList(tags) : Collections.emptyList();
        components = (components != null) ? Collections.unmodifiableMap(components) : Collections.emptyMap();
    }

    public static EntityDefinition create(
        int version,
        String id,
        List<String> tags,
        String debugColor,
        boolean placeable,
        Map<String, Map<String, Object>> components
    ) {
        return new EntityDefinition(version, id, tags, debugColor, placeable, components);
    }
}
