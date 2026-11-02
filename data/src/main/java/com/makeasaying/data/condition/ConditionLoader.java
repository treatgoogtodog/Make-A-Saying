package com.makeasaying.data.condition;

import com.fasterxml.jackson.databind.JsonNode;
import com.makeasaying.engine.condition.AtomRegistry;
import com.makeasaying.engine.condition.CompiledCondition;
import com.makeasaying.engine.flags.FlagDefinition;
import com.makeasaying.engine.flags.FlagRegistry;
import com.makeasaying.engine.flags.FlagType;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses and compiles JSON condition AST trees into flat, allocation-free CompiledCondition arrays.
 */
public final class ConditionLoader {
    public static final int MAX_DEPTH = 8;

    private ConditionLoader() {}

    public static CompiledCondition parse(JsonNode root, FlagRegistry registry, AtomRegistry atomRegistry) {
        if (root == null || root.isNull() || (root.isObject() && root.isEmpty())) {
            return CompiledCondition.ALWAYS_TRUE;
        }

        List<CompiledCondition.Node> nodes = new ArrayList<>();
        compileNode(root, registry, atomRegistry, nodes, 1);
        return new CompiledCondition(nodes.toArray(new CompiledCondition.Node[0]));
    }

    private static int compileNode(
        JsonNode node,
        FlagRegistry registry,
        AtomRegistry atomRegistry,
        List<CompiledCondition.Node> nodes,
        int depth
    ) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException(String.format("COND_ERR_DEPTH_EXCEEDED: Condition nesting depth %d exceeds maximum %d", depth, MAX_DEPTH));
        }

        if (node == null || node.isNull() || (node.isObject() && node.isEmpty())) {
            int idx = nodes.size();
            nodes.add(new CompiledCondition.Node(CompiledCondition.OP_ALWAYS_TRUE, -1, 0, new int[0]));
            return idx;
        }

        if (!node.isObject()) {
            throw new IllegalArgumentException("Condition node must be a JSON object: " + node);
        }

        // 1. Check combinators
        if (node.has("all")) {
            JsonNode allArr = node.get("all");
            if (!allArr.isArray()) throw new IllegalArgumentException("'all' combinator must be an array");
            if (allArr.isEmpty()) {
                int idx = nodes.size();
                nodes.add(new CompiledCondition.Node(CompiledCondition.OP_ALWAYS_TRUE, -1, 0, new int[0]));
                return idx;
            }

            int myIdx = nodes.size();
            nodes.add(null); // placeholder
            int[] childIndices = new int[allArr.size()];
            for (int i = 0; i < allArr.size(); i++) {
                childIndices[i] = compileNode(allArr.get(i), registry, atomRegistry, nodes, depth + 1);
            }
            nodes.set(myIdx, new CompiledCondition.Node(CompiledCondition.OP_ALL, -1, 0, childIndices));
            return myIdx;
        }

        if (node.has("any")) {
            JsonNode anyArr = node.get("any");
            if (!anyArr.isArray()) throw new IllegalArgumentException("'any' combinator must be an array");
            if (anyArr.isEmpty()) {
                int idx = nodes.size();
                nodes.add(new CompiledCondition.Node(CompiledCondition.OP_ALWAYS_FALSE, -1, 0, new int[0]));
                return idx;
            }

            int myIdx = nodes.size();
            nodes.add(null); // placeholder
            int[] childIndices = new int[anyArr.size()];
            for (int i = 0; i < anyArr.size(); i++) {
                childIndices[i] = compileNode(anyArr.get(i), registry, atomRegistry, nodes, depth + 1);
            }
            nodes.set(myIdx, new CompiledCondition.Node(CompiledCondition.OP_ANY, -1, 0, childIndices));
            return myIdx;
        }

        if (node.has("not")) {
            JsonNode notChild = node.get("not");
            int myIdx = nodes.size();
            nodes.add(null); // placeholder
            int childIdx = compileNode(notChild, registry, atomRegistry, nodes, depth + 1);
            nodes.set(myIdx, new CompiledCondition.Node(CompiledCondition.OP_NOT, -1, 0, new int[] { childIdx }));
            return myIdx;
        }

        // 2. Atoms
        // Check registered atoms
        java.util.Iterator<String> fieldNames = node.fieldNames();
        String atomKey = null;
        while (fieldNames.hasNext()) {
            String f = fieldNames.next();
            if (atomRegistry.isRegistered(f)) {
                atomKey = f;
                break;
            }
        }

        if (atomKey == null) {
            throw new IllegalArgumentException("COND_ERR_UNKNOWN_ATOM: Unknown condition atom node: " + node);
        }

        if ("flag_set".equals(atomKey)) {
            String flagId = node.get("flag_set").asText();
            FlagDefinition def = registry.get(flagId);
            if (def == null) {
                throw new IllegalArgumentException("COND_ERR_UNKNOWN_FLAG: Unknown flag ID '" + flagId + "' in condition");
            }
            if (def.type() == FlagType.ENUM) {
                throw new IllegalArgumentException("COND_ERR_FLAG_SET_ON_ENUM: flag_set cannot be used on enum flag '" + flagId + "' (use == instead)");
            }
            int myIdx = nodes.size();
            nodes.add(new CompiledCondition.Node(CompiledCondition.OP_FLAG_SET, def.index(), 0, new int[0]));
            return myIdx;
        }

        if ("flag".equals(atomKey)) {
            String flagId = node.get("flag").asText();
            FlagDefinition def = registry.get(flagId);
            if (def == null) {
                throw new IllegalArgumentException("COND_ERR_UNKNOWN_FLAG: Unknown flag ID '" + flagId + "' in condition");
            }

            String opStr = node.path("op").asText("==");
            int op;
            switch (opStr) {
                case "==" -> op = CompiledCondition.OP_FLAG_EQ;
                case "!=" -> op = CompiledCondition.OP_FLAG_NE;
                case "<" -> op = CompiledCondition.OP_FLAG_LT;
                case "<=" -> op = CompiledCondition.OP_FLAG_LE;
                case ">" -> op = CompiledCondition.OP_FLAG_GT;
                case ">=" -> op = CompiledCondition.OP_FLAG_GE;
                default -> throw new IllegalArgumentException("COND_ERR_INVALID_OP: Unknown comparison operator '" + opStr + "' in flag condition");
            }

            JsonNode valNode = node.get("value");
            if (valNode == null) {
                throw new IllegalArgumentException("Flag condition missing 'value' field");
            }

            int targetVal;
            if (def.type() == FlagType.BOOL) {
                if (valNode.isBoolean()) {
                    targetVal = valNode.asBoolean() ? 1 : 0;
                } else if ("true".equalsIgnoreCase(valNode.asText())) {
                    targetVal = 1;
                } else if ("false".equalsIgnoreCase(valNode.asText())) {
                    targetVal = 0;
                } else {
                    throw new IllegalArgumentException("COND_ERR_VALUE_OUT_OF_RANGE: Expected boolean for flag '" + flagId + "', got: " + valNode);
                }
            } else if (def.type() == FlagType.ENUM) {
                String enumStr = valNode.asText();
                targetVal = def.enumValues().indexOf(enumStr);
                if (targetVal < 0) {
                    throw new IllegalArgumentException(String.format("COND_ERR_UNKNOWN_ENUM_VALUE: Unknown enum value '%s' for flag '%s'. Valid: %s",
                        enumStr, flagId, def.enumValues()));
                }
            } else {
                if (!valNode.isNumber() && !valNode.asText().matches("^-?\\d+$")) {
                    throw new IllegalArgumentException("COND_ERR_VALUE_OUT_OF_RANGE: Expected integer for flag '" + flagId + "', got: " + valNode);
                }
                targetVal = valNode.asInt();
                if (targetVal < def.min() || targetVal > def.max()) {
                    throw new IllegalArgumentException(String.format("COND_ERR_VALUE_OUT_OF_RANGE: Value %d outside [%d..%d] for flag '%s'",
                        targetVal, def.min(), def.max(), flagId));
                }
            }

            int myIdx = nodes.size();
            nodes.add(new CompiledCondition.Node(op, def.index(), targetVal, new int[0]));
            return myIdx;
        }

        throw new IllegalArgumentException("COND_ERR_UNKNOWN_ATOM: Unhandled registered atom: " + atomKey);
    }
}
