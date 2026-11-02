package com.makeasaying.data.flags;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeasaying.engine.flags.FlagDefinition;
import com.makeasaying.engine.flags.FlagRegistry;
import com.makeasaying.engine.flags.FlagScope;
import com.makeasaying.engine.flags.FlagType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Loader and validator for flag definitions from JSON (content/flags.json).
 */
public final class FlagDefinitionLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_.]+$");

    private FlagDefinitionLoader() {}

    public static FlagRegistry load(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Flags file not found: " + path);
        }
        JsonNode root = MAPPER.readTree(path.toFile());
        return parse(root);
    }

    public static FlagRegistry parse(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Flags JSON root must be an object");
        }

        int version = root.path("version").asInt(-1);
        if (version != 1) {
            throw new IllegalArgumentException("Unsupported flags version: " + version + " (expected 1)");
        }

        JsonNode flagsNode = root.get("flags");
        if (flagsNode == null || !flagsNode.isObject()) {
            throw new IllegalArgumentException("Flags file missing 'flags' object");
        }

        List<FlagDefinition> definitions = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        Iterator<Map.Entry<String, JsonNode>> fields = flagsNode.fields();
        int index = 0;
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String id = entry.getKey();
            JsonNode defNode = entry.getValue();

            if (!ID_PATTERN.matcher(id).matches()) {
                throw new IllegalArgumentException(String.format("FLAG_ERR_INVALID_ID: Flag ID '%s' must match %s", id, ID_PATTERN.pattern()));
            }
            if (!seenIds.add(id)) {
                throw new IllegalArgumentException(String.format("FLAG_ERR_DUPLICATE_ID: Duplicate flag ID '%s'", id));
            }

            String typeStr = defNode.path("type").asText(null);
            if (typeStr == null) {
                throw new IllegalArgumentException(String.format("FLAG_ERR_UNKNOWN_TYPE: Flag '%s' missing 'type'", id));
            }

            FlagType type;
            try {
                type = FlagType.valueOf(typeStr.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(String.format("FLAG_ERR_UNKNOWN_TYPE: Flag '%s' has unknown type '%s'", id, typeStr));
            }

            String scopeStr = defNode.path("scope").asText(null);
            if (scopeStr == null) {
                throw new IllegalArgumentException(String.format("FLAG_ERR_UNKNOWN_SCOPE: Flag '%s' missing 'scope'", id));
            }

            FlagScope scope;
            try {
                scope = FlagScope.valueOf(scopeStr.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(String.format("FLAG_ERR_UNKNOWN_SCOPE: Flag '%s' has unknown scope '%s'", id, scopeStr));
            }

            String doc = defNode.path("doc").asText("");

            int defaultValue = 0;
            int min = 0;
            int max = 0;
            List<String> enumValues = null;

            switch (type) {
                case BOOL -> {
                    min = 0;
                    max = 1;
                    if (!defNode.has("default")) {
                        defaultValue = 0;
                    } else if (defNode.get("default").isBoolean()) {
                        defaultValue = defNode.get("default").asBoolean() ? 1 : 0;
                    } else {
                        throw new IllegalArgumentException(String.format("FLAG_ERR_INVALID_DEFAULT: Flag '%s' default must be boolean", id));
                    }
                }
                case INT -> {
                    if (!defNode.has("min") || !defNode.has("max")) {
                        throw new IllegalArgumentException(String.format("FLAG_ERR_MISSING_BOUNDS: Flag '%s' requires 'min' and 'max'", id));
                    }
                    min = defNode.get("min").asInt();
                    max = defNode.get("max").asInt();
                    if (min > max) {
                        throw new IllegalArgumentException(String.format("FLAG_ERR_INVALID_BOUNDS: Flag '%s' min (%d) > max (%d)", id, min, max));
                    }
                    if (!defNode.has("default")) {
                        defaultValue = min;
                    } else {
                        defaultValue = defNode.get("default").asInt();
                        if (defaultValue < min || defaultValue > max) {
                            throw new IllegalArgumentException(String.format("FLAG_ERR_DEFAULT_OUT_OF_RANGE: Flag '%s' default %d outside [%d..%d]", id, defaultValue, min, max));
                        }
                    }
                }
                case ENUM -> {
                    JsonNode valuesNode = defNode.get("values");
                    if (valuesNode == null || !valuesNode.isArray() || valuesNode.isEmpty()) {
                        throw new IllegalArgumentException(String.format("FLAG_ERR_MISSING_VALUES: Enum flag '%s' requires non-empty 'values' array", id));
                    }
                    enumValues = new ArrayList<>();
                    Set<String> seenValues = new HashSet<>();
                    for (JsonNode v : valuesNode) {
                        String str = v.asText();
                        if (!seenValues.add(str)) {
                            throw new IllegalArgumentException(String.format("FLAG_ERR_DUPLICATE_ENUM_VALUE: Duplicate enum value '%s' in flag '%s'", str, id));
                        }
                        enumValues.add(str);
                    }
                    min = 0;
                    max = enumValues.size() - 1;

                    String defStr = defNode.path("default").asText(null);
                    if (defStr == null) {
                        defaultValue = 0;
                    } else {
                        defaultValue = enumValues.indexOf(defStr);
                        if (defaultValue < 0) {
                            throw new IllegalArgumentException(String.format("FLAG_ERR_DEFAULT_NOT_IN_VALUES: Flag '%s' default '%s' not in %s", id, defStr, enumValues));
                        }
                    }
                }
            }

            definitions.add(new FlagDefinition(
                index++,
                id,
                type,
                scope,
                defaultValue,
                min,
                max,
                enumValues,
                doc
            ));
        }

        return new FlagRegistry(definitions);
    }
}
