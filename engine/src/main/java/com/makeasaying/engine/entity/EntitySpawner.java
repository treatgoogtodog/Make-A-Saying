package com.makeasaying.engine.entity;

import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.schema.ComponentField;
import com.makeasaying.engine.schema.ComponentSchema;
import com.makeasaying.engine.schema.ComponentSchemaRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * Single pipeline for spawning entities from definitions and overrides per AGENTS.md M6b brief.
 * Sets initial position, zeros subpixel remainder, and resets prev position to prevent interpolation smear.
 */
public final class EntitySpawner {
    private final ComponentSchemaRegistry schemaRegistry;

    public EntitySpawner(ComponentSchemaRegistry schemaRegistry) {
        if (schemaRegistry == null) throw new IllegalArgumentException("schemaRegistry cannot be null");
        this.schemaRegistry = schemaRegistry;
    }

    public long spawn(
        World world,
        EntityDefinition def,
        String instanceId,
        int x,
        int y,
        Map<String, Map<String, Object>> overrides
    ) {
        if (world == null) throw new IllegalArgumentException("world cannot be null");
        if (def == null) throw new IllegalArgumentException("EntityDefinition cannot be null");

        long entityId = world.createEntity();

        // 1. Initial body position (spawn origin)
        BodyComponent body = new BodyComponent(x, y);
        body.prevX = x;
        body.prevY = y;
        body.subpixelX = 0f;
        body.subpixelY = 0f;
        world.setBody(entityId, body);

        // 2. Set instance ID string
        if (instanceId != null) {
            world.setInstanceId(entityId, instanceId);
        }

        // 3. Apply definition components
        for (Map.Entry<String, Map<String, Object>> compEntry : def.components().entrySet()) {
            String compId = compEntry.getKey();
            Map<String, Object> baseValues = compEntry.getValue();

            ComponentSchema schema = schemaRegistry.getSchema(compId);
            if (schema == null) {
                continue; // Handled by loader/validator
            }

            Map<String, Object> mergedValues = new HashMap<>(baseValues);
            if (compId.equals("body")) {
                mergedValues.put("x", x);
                mergedValues.put("y", y);
            }

            // Apply overrides if present
            if (overrides != null && overrides.containsKey(compId)) {
                Map<String, Object> compOverrides = overrides.get(compId);
                if (compOverrides != null) {
                    for (Map.Entry<String, Object> overrideEntry : compOverrides.entrySet()) {
                        String fieldName = overrideEntry.getKey();
                        ComponentField field = schema.getField(fieldName);
                        if (field != null && field.overridable()) {
                            mergedValues.put(fieldName, overrideEntry.getValue());
                        }
                    }
                }
            }

            // Apply to entity storage via schema
            schema.apply(world, entityId, mergedValues);
        }

        // Re-enforce body position reset after schema application
        BodyComponent finalBody = world.getBody(entityId);
        if (finalBody != null) {
            finalBody.x = x;
            finalBody.y = y;
            finalBody.prevX = x;
            finalBody.prevY = y;
            finalBody.subpixelX = 0f;
            finalBody.subpixelY = 0f;
        }

        return entityId;
    }

    public ComponentSchemaRegistry getSchemaRegistry() {
        return schemaRegistry;
    }
}
