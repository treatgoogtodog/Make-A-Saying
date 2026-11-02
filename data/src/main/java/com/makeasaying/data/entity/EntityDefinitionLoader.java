package com.makeasaying.data.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeasaying.engine.entity.EntityDefinition;
import com.makeasaying.engine.schema.ComponentField;
import com.makeasaying.engine.schema.ComponentSchema;
import com.makeasaying.engine.schema.ComponentSchemaRegistry;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loads and validates EntityDefinition files per AGENTS.md M6b brief.
 * Validates with exact error codes and precise file/field locations.
 */
public final class EntityDefinitionLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern VALID_ID_PATTERN = Pattern.compile("^[a-z0-9_.]+$");

    public static final String DEF_ERR_UNKNOWN_COMPONENT = "DEF_ERR_UNKNOWN_COMPONENT";
    public static final String DEF_ERR_UNKNOWN_FIELD = "DEF_ERR_UNKNOWN_FIELD";
    public static final String DEF_ERR_TYPE_MISMATCH = "DEF_ERR_TYPE_MISMATCH";
    public static final String DEF_ERR_OUT_OF_RANGE = "DEF_ERR_OUT_OF_RANGE";
    public static final String DEF_ERR_MISSING_REQUIRED = "DEF_ERR_MISSING_REQUIRED";
    public static final String DEF_ERR_INVALID_ID = "DEF_ERR_INVALID_ID";

    private EntityDefinitionLoader() {}

    public static EntityDefinition load(Path path, ComponentSchemaRegistry schemaRegistry) throws IOException {
        if (!Files.exists(path)) {
            Log.error(LogCategory.DATA, "Entity definition file does not exist at: %s", path.toAbsolutePath());
            throw new IllegalArgumentException("Entity definition not found: " + path);
        }
        String content = Files.readString(path);
        return parse(content, schemaRegistry, path.toString());
    }

    public static EntityDefinition parse(String jsonString, ComponentSchemaRegistry schemaRegistry, String source) {
        if (schemaRegistry == null) throw new IllegalArgumentException("schemaRegistry cannot be null");

        JsonNode root;
        try {
            root = MAPPER.readTree(jsonString);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse JSON in " + source + ": " + e.getMessage(), e);
        }

        if (!root.has("version") || root.get("version").asInt() <= 0) {
            throw new IllegalStateException(source + ": missing or invalid 'version' field");
        }
        int version = root.get("version").asInt();

        if (!root.has("id") || !root.get("id").isTextual()) {
            throw new IllegalStateException(source + ": missing 'id' field");
        }
        String id = root.get("id").asText();
        if (!VALID_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalStateException(DEF_ERR_INVALID_ID + ": Invalid ID '" + id + "' in " + source + ". Must match [a-z0-9_.]");
        }

        List<String> tags = new ArrayList<>();
        if (root.has("tags") && root.get("tags").isArray()) {
            for (JsonNode t : root.get("tags")) {
                tags.add(t.asText());
            }
        }

        String debugColor = root.has("debugColor") ? root.get("debugColor").asText() : "#FFFFFF";
        boolean placeable = !root.has("placeable") || root.get("placeable").asBoolean(true);

        if (!root.has("components") || !root.get("components").isObject()) {
            throw new IllegalStateException(DEF_ERR_MISSING_REQUIRED + ": Missing 'components' object in " + source);
        }

        JsonNode componentsNode = root.get("components");
        Map<String, Map<String, Object>> componentsMap = new LinkedHashMap<>();

        // Validate required components (e.g. body must always exist)
        if (!componentsNode.has("body")) {
            throw new IllegalStateException(DEF_ERR_MISSING_REQUIRED + ": Component 'body' is required in definition " + source);
        }

        Iterator<Map.Entry<String, JsonNode>> compFields = componentsNode.fields();
        while (compFields.hasNext()) {
            Map.Entry<String, JsonNode> compEntry = compFields.next();
            String compId = compEntry.getKey();
            JsonNode compValueNode = compEntry.getValue();

            // 1. Unknown component check
            ComponentSchema schema = schemaRegistry.getSchema(compId);
            if (schema == null) {
                throw new IllegalStateException(DEF_ERR_UNKNOWN_COMPONENT + ": Unknown component '" + compId + "' in " + source);
            }

            if (!compValueNode.isObject()) {
                throw new IllegalStateException(DEF_ERR_TYPE_MISMATCH + ": Component '" + compId + "' must be an object in " + source);
            }

            Map<String, Object> valuesMap = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fieldEntries = compValueNode.fields();
            while (fieldEntries.hasNext()) {
                Map.Entry<String, JsonNode> fieldEntry = fieldEntries.next();
                String fieldName = fieldEntry.getKey();
                JsonNode val = fieldEntry.getValue();

                // 2. Unknown field check
                ComponentField field = schema.getField(fieldName);
                if (field == null) {
                    throw new IllegalStateException(DEF_ERR_UNKNOWN_FIELD + ": Unknown field '" + fieldName + "' in component '" + compId + "' in " + source);
                }

                // 3. Type mismatch & out-of-range check
                switch (field.type()) {
                    case INT -> {
                        if (!val.isIntegralNumber()) {
                            throw new IllegalStateException(DEF_ERR_TYPE_MISMATCH + ": Field '" + fieldName + "' in component '" + compId + "' must be an integer in " + source);
                        }
                        int iv = val.asInt();
                        if (field.min() != null && iv < field.min().intValue()) {
                            throw new IllegalStateException(DEF_ERR_OUT_OF_RANGE + ": Field '" + fieldName + "' (" + iv + ") is less than min " + field.min() + " in " + source);
                        }
                        if (field.max() != null && iv > field.max().intValue()) {
                            throw new IllegalStateException(DEF_ERR_OUT_OF_RANGE + ": Field '" + fieldName + "' (" + iv + ") is greater than max " + field.max() + " in " + source);
                        }
                        valuesMap.put(fieldName, iv);
                    }
                    case FLOAT -> {
                        if (!val.isNumber()) {
                            throw new IllegalStateException(DEF_ERR_TYPE_MISMATCH + ": Field '" + fieldName + "' in component '" + compId + "' must be a number in " + source);
                        }
                        float fv = (float) val.asDouble();
                        if (field.min() != null && fv < field.min().floatValue()) {
                            throw new IllegalStateException(DEF_ERR_OUT_OF_RANGE + ": Field '" + fieldName + "' (" + fv + ") is less than min " + field.min() + " in " + source);
                        }
                        if (field.max() != null && fv > field.max().floatValue()) {
                            throw new IllegalStateException(DEF_ERR_OUT_OF_RANGE + ": Field '" + fieldName + "' (" + fv + ") is greater than max " + field.max() + " in " + source);
                        }
                        valuesMap.put(fieldName, fv);
                    }
                    case BOOL -> {
                        if (!val.isBoolean()) {
                            throw new IllegalStateException(DEF_ERR_TYPE_MISMATCH + ": Field '" + fieldName + "' in component '" + compId + "' must be a boolean in " + source);
                        }
                        valuesMap.put(fieldName, val.asBoolean());
                    }
                    case ENUM -> {
                        if (!val.isTextual()) {
                            throw new IllegalStateException(DEF_ERR_TYPE_MISMATCH + ": Field '" + fieldName + "' in component '" + compId + "' must be an enum string in " + source);
                        }
                        String s = val.asText();
                        if (!field.enumConstants().contains(s)) {
                            throw new IllegalStateException(DEF_ERR_OUT_OF_RANGE + ": Value '" + s + "' for field '" + fieldName + "' is not a valid enum constant " + field.enumConstants() + " in " + source);
                        }
                        valuesMap.put(fieldName, s);
                    }
                    case STRING -> {
                        if (!val.isTextual()) {
                            throw new IllegalStateException(DEF_ERR_TYPE_MISMATCH + ": Field '" + fieldName + "' in component '" + compId + "' must be a string in " + source);
                        }
                        valuesMap.put(fieldName, val.asText());
                    }
                }
            }
            componentsMap.put(compId, valuesMap);
        }

        return new EntityDefinition(version, id, tags, debugColor, placeable, componentsMap);
    }
}
