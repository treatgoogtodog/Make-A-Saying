package com.makeasaying.data.input;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeasaying.engine.input.ActionDefinition;
import com.makeasaying.engine.input.ActionRegistry;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and validates action definitions from JSON per AGENTS.md section 7.4.
 */
public final class ActionLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ActionLoader() {}

    public static ActionRegistry load(Path path) throws IOException {
        if (!Files.exists(path)) {
            Log.error(LogCategory.DATA, "Actions file does not exist at: %s", path.toAbsolutePath());
            throw new IllegalArgumentException("Actions file not found: " + path);
        }

        JsonNode root = MAPPER.readTree(path.toFile());
        return parse(root, path.toString());
    }

    public static ActionRegistry parse(JsonNode root, String source) {
        if (!root.has("version") || root.get("version").asInt() <= 0) {
            throw new IllegalStateException("Missing or invalid 'version' field in " + source);
        }

        ActionRegistry registry = new ActionRegistry();
        JsonNode actionsNode = root.get("actions");
        if (actionsNode == null || !actionsNode.isArray()) {
            throw new IllegalStateException("Missing 'actions' array in " + source);
        }

        for (JsonNode node : actionsNode) {
            String id = node.get("id").asText();
            String typeStr = node.get("type").asText();
            boolean reserved = node.has("reserved") && node.get("reserved").asBoolean();

            if ("button".equalsIgnoreCase(typeStr)) {
                registry.register(ActionDefinition.button(id, reserved));
            } else if ("axis".equalsIgnoreCase(typeStr)) {
                String neg = node.has("negativeButton") ? node.get("negativeButton").asText() : null;
                String pos = node.has("positiveButton") ? node.get("positiveButton").asText() : null;
                if (neg == null || pos == null) {
                    throw new IllegalStateException("Axis action '" + id + "' must declare negativeButton and positiveButton in " + source);
                }
                registry.register(ActionDefinition.axis(id, neg, pos));
            } else {
                throw new IllegalStateException("Unknown action type '" + typeStr + "' for action '" + id + "' in " + source);
            }
        }

        // Validate axis sub-buttons exist
        for (ActionDefinition def : registry.getAll()) {
            if (def.type() == ActionDefinition.ActionType.AXIS) {
                if (!registry.contains(def.negativeButton())) {
                    throw new IllegalStateException("Axis '" + def.id() + "' references undeclared negativeButton '" + def.negativeButton() + "' in " + source);
                }
                if (!registry.contains(def.positiveButton())) {
                    throw new IllegalStateException("Axis '" + def.id() + "' references undeclared positiveButton '" + def.positiveButton() + "' in " + source);
                }
            }
        }

        return registry;
    }
}
