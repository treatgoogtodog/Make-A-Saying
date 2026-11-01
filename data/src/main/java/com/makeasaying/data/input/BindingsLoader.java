package com.makeasaying.data.input;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeasaying.engine.input.ActionDefinition;
import com.makeasaying.engine.input.ActionRegistry;
import com.makeasaying.engine.input.ContextBindings;
import com.makeasaying.engine.input.InputContext;
import com.makeasaying.engine.input.KeyNames;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Loads and validates key bindings from JSON per AGENTS.md section 7.4.
 * Loudly validates unknown keys, unknown actions, duplicate bindings, and unbound reserved actions.
 */
public final class BindingsLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BindingsLoader() {}

    public static ContextBindings load(Path path, ActionRegistry registry) throws IOException {
        if (!Files.exists(path)) {
            Log.error(LogCategory.DATA, "Bindings file does not exist at: %s", path.toAbsolutePath());
            throw new IllegalArgumentException("Bindings file not found: " + path);
        }

        JsonNode root = MAPPER.readTree(path.toFile());
        return parse(root, registry, path.toString());
    }

    public static ContextBindings parse(JsonNode root, ActionRegistry registry, String source) {
        if (!root.has("version") || root.get("version").asInt() <= 0) {
            throw new IllegalStateException("Missing or invalid 'version' field in " + source);
        }

        JsonNode contextsNode = root.get("contexts");
        if (contextsNode == null || !contextsNode.isObject()) {
            throw new IllegalStateException("Missing 'contexts' object in " + source);
        }

        ContextBindings bindings = new ContextBindings();

        Iterator<Map.Entry<String, JsonNode>> contextFields = contextsNode.fields();
        while (contextFields.hasNext()) {
            Map.Entry<String, JsonNode> contextEntry = contextFields.next();
            String contextName = contextEntry.getKey();
            JsonNode actionMapNode = contextEntry.getValue();

            Map<Integer, String> boundKeysInThisContext = new HashMap<>();

            Iterator<Map.Entry<String, JsonNode>> actionFields = actionMapNode.fields();
            while (actionFields.hasNext()) {
                Map.Entry<String, JsonNode> actionEntry = actionFields.next();
                String actionId = actionEntry.getKey();
                JsonNode keysNode = actionEntry.getValue();

                // 1. Validate action exists in registry
                if (!registry.contains(actionId)) {
                    throw new IllegalStateException("Unknown action '" + actionId + "' bound in context '" + contextName + "' in " + source);
                }

                if (!keysNode.isArray()) {
                    throw new IllegalStateException("Bindings for action '" + actionId + "' must be an array of key strings in " + source);
                }

                for (JsonNode keyNode : keysNode) {
                    String keyName = keyNode.asText();

                    // 2. Validate key name is known
                    if (!KeyNames.isValidKeyName(keyName)) {
                        throw new IllegalStateException("Unknown key name '" + keyName + "' bound to action '" + actionId + "' in " + source);
                    }

                    int keyCode = KeyNames.toKeyCode(keyName);

                    // 3. Validate duplicate binding in same context
                    if (boundKeysInThisContext.containsKey(keyCode)) {
                        String existingAction = boundKeysInThisContext.get(keyCode);
                        throw new IllegalStateException("Duplicate binding: Key '" + keyName + "' bound to both '" +
                            existingAction + "' and '" + actionId + "' in context '" + contextName + "' in " + source);
                    }

                    boundKeysInThisContext.put(keyCode, actionId);
                    bindings.bind(contextName, actionId, keyCode);
                }
            }
        }

        // 4. Validate reserved actions are not left unbound
        for (ActionDefinition actionDef : registry.getAll()) {
            if (actionDef.reserved()) {
                boolean boundGlobally = bindings.hasBindingForAction(InputContext.GLOBAL, actionDef.id());
                boolean boundGameplay = bindings.hasBindingForAction(InputContext.GAMEPLAY, actionDef.id());
                if (!boundGlobally && !boundGameplay) {
                    throw new IllegalStateException("Reserved action '" + actionDef.id() + "' must be bound in global or gameplay context in " + source);
                }
            }
        }

        return bindings;
    }
}
