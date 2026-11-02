package com.makeasaying.editor.map.model;

import com.makeasaying.engine.entity.EntityDefinition;
import com.makeasaying.engine.schema.ComponentField;
import com.makeasaying.engine.schema.ComponentSchema;
import com.makeasaying.engine.schema.ComponentSchemaRegistry;
import com.makeasaying.engine.world.RoomEntityData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure model for driving schema-based inspector UI per AGENTS.md section 7.2.
 * Single source of truth: ComponentSchemaRegistry drives all field rendering,
 * overridable state, ranges, and reset-to-default logic.
 */
public final class InspectorModel {
    public record FieldNode(
        ComponentField field,
        Object currentValue,
        boolean isOverridden,
        boolean isOverridable
    ) {}

    public record ComponentNode(
        String componentId,
        ComponentSchema schema,
        List<FieldNode> fields
    ) {}

    private final RoomEntityData entity;
    private final EntityDefinition def;
    private final ComponentSchemaRegistry registry;

    public InspectorModel(RoomEntityData entity, EntityDefinition def, ComponentSchemaRegistry registry) {
        this.entity = entity;
        this.def = def;
        this.registry = registry;
    }

    public List<ComponentNode> buildComponentNodes() {
        if (entity == null || def == null || registry == null) {
            return Collections.emptyList();
        }

        List<ComponentNode> nodes = new ArrayList<>();
        Map<String, Map<String, Object>> baseComps = def.components();
        Map<String, Map<String, Object>> overrides = entity.overrides();

        for (Map.Entry<String, Map<String, Object>> entry : baseComps.entrySet()) {
            String compId = entry.getKey();
            ComponentSchema schema = registry.getSchema(compId);
            if (schema == null) continue;

            Map<String, Object> baseFields = entry.getValue();
            Map<String, Object> compOverrides = (overrides != null) ? overrides.get(compId) : null;

            List<FieldNode> fieldNodes = new ArrayList<>();
            for (ComponentField field : schema.fields().values()) {
                boolean isOverridden = compOverrides != null && compOverrides.containsKey(field.name());
                Object currentVal = isOverridden
                    ? compOverrides.get(field.name())
                    : (baseFields != null && baseFields.containsKey(field.name())
                        ? baseFields.get(field.name())
                        : field.defaultValue());

                fieldNodes.add(new FieldNode(field, currentVal, isOverridden, field.overridable()));
            }

            nodes.add(new ComponentNode(compId, schema, fieldNodes));
        }

        return nodes;
    }

    public static Map<String, Map<String, Object>> setOverride(
        Map<String, Map<String, Object>> currentOverrides,
        String componentId,
        String fieldName,
        Object newValue
    ) {
        Map<String, Map<String, Object>> newOverrides = new LinkedHashMap<>();
        if (currentOverrides != null) {
            for (Map.Entry<String, Map<String, Object>> e : currentOverrides.entrySet()) {
                newOverrides.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
            }
        }

        Map<String, Object> compMap = newOverrides.computeIfAbsent(componentId, k -> new LinkedHashMap<>());
        compMap.put(fieldName, newValue);
        return newOverrides;
    }

    public static Map<String, Map<String, Object>> removeOverride(
        Map<String, Map<String, Object>> currentOverrides,
        String componentId,
        String fieldName
    ) {
        if (currentOverrides == null) return Collections.emptyMap();

        Map<String, Map<String, Object>> newOverrides = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : currentOverrides.entrySet()) {
            newOverrides.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
        }

        if (newOverrides.containsKey(componentId)) {
            Map<String, Object> compMap = newOverrides.get(componentId);
            compMap.remove(fieldName);
            if (compMap.isEmpty()) {
                newOverrides.remove(componentId);
            }
        }

        return newOverrides;
    }
}
