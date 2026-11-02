package com.makeasaying.data.save;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.makeasaying.engine.flags.FlagDefinition;
import com.makeasaying.engine.flags.FlagRegistry;
import com.makeasaying.engine.flags.FlagType;
import com.makeasaying.engine.save.SaveEnvelope;
import com.makeasaying.engine.save.SavePayload;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Serializes checkpoint save data into canonical JSON with SHA-256 checksums per M7b brief.
 * Formats typed flags (booleans as booleans, ints as numbers, enums by name).
 * Preserves unknown reserved sections byte-equivalently.
 */
public final class SaveWriter {
    public static final int CURRENT_VERSION = 1;
    public static final String GAME_VERSION = "0.1.0";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private SaveWriter() {}

    /**
     * Serializes a SavePayload into a complete, checksummed JSON envelope string.
     */
    public static String serialize(SavePayload payload, FlagRegistry flagRegistry) {
        if (payload == null) throw new IllegalArgumentException("payload must not be null");

        try {
            // 1. Build canonical "data" JSON tree
            ObjectNode dataNode = MAPPER.createObjectNode();
            dataNode.put("playtimeTicks", payload.playtimeTicks());
            dataNode.put("timestampUtc", payload.timestampUtc());
            dataNode.put("currentRoomId", payload.currentRoomId());
            dataNode.put("savepointInstanceId", payload.savepointInstanceId());

            // Global flags with typed formatting
            ObjectNode flagsNode = MAPPER.createObjectNode();
            for (Map.Entry<String, Object> entry : payload.globalFlags().entrySet()) {
                String flagId = entry.getKey();
                Object rawVal = entry.getValue();
                FlagDefinition def = flagRegistry != null ? flagRegistry.get(flagId) : null;

                if (def != null && def.type() == FlagType.BOOL) {
                    boolean b = (rawVal instanceof Boolean) ? (Boolean) rawVal : (((Number) rawVal).intValue() != 0);
                    flagsNode.put(flagId, b);
                } else if (def != null && def.type() == FlagType.ENUM) {
                    if (rawVal instanceof String s) {
                        flagsNode.put(flagId, s);
                    } else if (rawVal instanceof Number n) {
                        int idx = n.intValue();
                        String name = (idx >= 0 && idx < def.enumValues().size()) ? def.enumValues().get(idx) : "unknown";
                        flagsNode.put(flagId, name);
                    }
                } else if (rawVal instanceof Number n) {
                    flagsNode.put(flagId, n.intValue());
                } else if (rawVal instanceof Boolean b) {
                    flagsNode.put(flagId, b);
                } else if (rawVal != null) {
                    flagsNode.put(flagId, rawVal.toString());
                }
            }
            dataNode.set("globalFlags", flagsNode);

            // Room records (serialized as tree)
            dataNode.set("roomRecords", MAPPER.valueToTree(payload.roomRecords()));

            // Write back any preserved reserved sections untouched
            for (Map.Entry<String, Object> entry : payload.reservedSections().entrySet()) {
                String secName = entry.getKey();
                Object secVal = entry.getValue();
                if (secVal instanceof JsonNode jn) {
                    dataNode.set(secName, jn);
                } else {
                    dataNode.set(secName, MAPPER.valueToTree(secVal));
                }
            }

            // 2. Compute SHA-256 over canonical data representation
            String canonicalDataJson = toCanonicalJson(dataNode);
            String checksum = computeSha256(canonicalDataJson);

            // 3. Wrap in envelope with version first
            ObjectNode envelope = MAPPER.createObjectNode();
            envelope.put("version", CURRENT_VERSION);
            envelope.put("gameVersion", GAME_VERSION);
            envelope.put("checksum", checksum);
            envelope.set("data", dataNode);

            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            printer.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
            return MAPPER.writer(printer).writeValueAsString(envelope) + "\n";
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize save data", e);
        }
    }

    public static String computeSha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Produces canonical JSON (sorted keys at all object levels, no extra whitespace)
     * for checksum computation and verification.
     */
    public static String toCanonicalJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isObject()) {
            StringBuilder sb = new StringBuilder("{");
            List<String> fieldNames = new ArrayList<>();
            Iterator<String> it = node.fieldNames();
            while (it.hasNext()) {
                fieldNames.add(it.next());
            }
            Collections.sort(fieldNames);
            boolean first = true;
            for (String fieldName : fieldNames) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(escapeJson(fieldName)).append("\":");
                sb.append(toCanonicalJson(node.get(fieldName)));
            }
            sb.append('}');
            return sb.toString();
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (int i = 0; i < node.size(); i++) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(toCanonicalJson(node.get(i)));
            }
            sb.append(']');
            return sb.toString();
        }
        return node.toString();
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
