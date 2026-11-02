package com.makeasaying.engine.schema;

import com.makeasaying.engine.entity.World;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Pure component schema specification mapping between abstract property values and
 * the engine's flat component arrays.
 */
public final class ComponentSchema {
    public interface ComponentApplier {
        void apply(World world, long entityId, Map<String, Object> values);
    }

    public interface ComponentReader {
        Map<String, Object> read(World world, long entityId);
    }

    private final String id;
    private final Map<String, ComponentField> fields;
    private final ComponentApplier applier;
    private final ComponentReader reader;

    public ComponentSchema(
        String id,
        List<ComponentField> fieldList,
        ComponentApplier applier,
        ComponentReader reader
    ) {
        this.id = id;
        Map<String, ComponentField> map = new LinkedHashMap<>();
        if (fieldList != null) {
            for (ComponentField f : fieldList) {
                map.put(f.name(), f);
            }
        }
        this.fields = Collections.unmodifiableMap(map);
        this.applier = applier;
        this.reader = reader;
    }

    public String id() {
        return id;
    }

    public Map<String, ComponentField> fields() {
        return fields;
    }

    public ComponentField getField(String name) {
        return fields.get(name);
    }

    public boolean hasField(String name) {
        return fields.containsKey(name);
    }

    public void apply(World world, long entityId, Map<String, Object> values) {
        if (applier != null) {
            applier.apply(world, entityId, values);
        }
    }

    public Map<String, Object> read(World world, long entityId) {
        if (reader != null) {
            return reader.read(world, entityId);
        }
        return Collections.emptyMap();
    }
}
