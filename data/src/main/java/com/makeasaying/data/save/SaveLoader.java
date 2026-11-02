package com.makeasaying.data.save;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.makeasaying.engine.flags.FlagDefinition;
import com.makeasaying.engine.flags.FlagRegistry;
import com.makeasaying.engine.flags.FlagScope;
import com.makeasaying.engine.flags.FlagType;
import com.makeasaying.engine.save.SaveEnvelope;
import com.makeasaying.engine.save.SavePayload;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Parses and validates checkpoint save files per M7b brief.
 * Verifies envelope schema, version, SHA-256 checksum, flag types/ranges, and room record flags.
 * Preserves unknown/reserved sections with loud warnings.
 */
public final class SaveLoader {
    public static final int CURRENT_VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private static final Set<String> KNOWN_DATA_FIELDS = Set.of(
        "playtimeTicks", "timestampUtc", "currentRoomId", "savepointInstanceId", "globalFlags", "roomRecords"
    );

    private SaveLoader() {}

    public static SaveEnvelope load(String json, FlagRegistry flagRegistry) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("SAVE_ERR_EMPTY_FILE: Save file content is empty");
        }

        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isObject()) {
                throw new IllegalArgumentException("SAVE_ERR_INVALID_ROOT: Save file root must be a JSON object");
            }

            // 1. Version check
            if (!root.has("version")) {
                throw new IllegalArgumentException("SAVE_ERR_MISSING_VERSION: Save envelope missing version field");
            }
            int version = root.get("version").asInt();
            if (version > CURRENT_VERSION) {
                throw new IllegalArgumentException(String.format("SAVE_ERR_UNKNOWN_NEWER_VERSION: Save version %d is newer than supported version %d", version, CURRENT_VERSION));
            }

            String gameVersion = root.path("gameVersion").asText("unknown");

            // 2. Checksum validation
            if (!root.has("checksum")) {
                throw new IllegalArgumentException("SAVE_ERR_MISSING_CHECKSUM: Save envelope missing checksum field");
            }
            String declaredChecksum = root.get("checksum").asText();
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isObject()) {
                throw new IllegalArgumentException("SAVE_ERR_MISSING_DATA: Save envelope missing data payload object");
            }

            String canonicalDataJson = SaveWriter.toCanonicalJson(dataNode);
            String computedChecksum = SaveWriter.computeSha256(canonicalDataJson);
            if (!declaredChecksum.equalsIgnoreCase(computedChecksum)) {
                throw new IllegalArgumentException(String.format("SAVE_ERR_CHECKSUM_MISMATCH: Computed checksum %s does not match declared %s (file tampering or corruption)",
                    computedChecksum, declaredChecksum));
            }

            // 3. Parse core fields
            long playtimeTicks = dataNode.path("playtimeTicks").asLong(0L);
            String timestampUtc = dataNode.path("timestampUtc").asText("");
            String currentRoomId = dataNode.path("currentRoomId").asText(null);
            String savepointInstanceId = dataNode.path("savepointInstanceId").asText(null);

            if (currentRoomId == null || currentRoomId.isBlank()) {
                throw new IllegalArgumentException("SAVE_ERR_MISSING_ROOM_ID: data.currentRoomId is missing or blank");
            }
            if (savepointInstanceId == null || savepointInstanceId.isBlank()) {
                throw new IllegalArgumentException("SAVE_ERR_MISSING_SAVEPOINT_ID: data.savepointInstanceId is missing or blank");
            }

            // 4. Validate Global Flags
            Map<String, Object> globalFlags = new LinkedHashMap<>();
            JsonNode flagsNode = dataNode.get("globalFlags");
            Map<String, JsonNode> authoredFlags = new LinkedHashMap<>();
            if (flagsNode != null && flagsNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = flagsNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> f = fields.next();
                    authoredFlags.put(f.getKey(), f.getValue());
                }
            }

            if (flagRegistry != null) {
                // Check authored flags for unknowns
                for (Map.Entry<String, JsonNode> entry : authoredFlags.entrySet()) {
                    String flagId = entry.getKey();
                    JsonNode valNode = entry.getValue();
                    FlagDefinition def = flagRegistry.get(flagId);

                    if (def == null || def.scope() != FlagScope.GLOBAL) {
                        Log.warn(LogCategory.DATA, "SAVE_WARN_UNKNOWN_FLAG: Dropping undeclared or non-global flag '%s' from save file", flagId);
                        continue;
                    }

                    int validatedVal = def.defaultValue();
                    if (def.type() == FlagType.BOOL) {
                        if (valNode.isBoolean()) {
                            validatedVal = valNode.asBoolean() ? 1 : 0;
                        } else if (valNode.isNumber() && (valNode.asInt() == 0 || valNode.asInt() == 1)) {
                            validatedVal = valNode.asInt();
                        } else {
                            Log.warn(LogCategory.DATA, "SAVE_WARN_FLAG_OUT_OF_RANGE: Flag '%s' expected boolean, got %s. Reset to default %d",
                                flagId, valNode, def.defaultValue());
                        }
                    } else if (def.type() == FlagType.ENUM) {
                        String enumName = valNode.asText();
                        int enumIdx = def.enumValues().indexOf(enumName);
                        if (enumIdx >= 0) {
                            validatedVal = enumIdx;
                        } else {
                            Log.warn(LogCategory.DATA, "SAVE_WARN_FLAG_OUT_OF_RANGE: Enum flag '%s' value '%s' not in valid enum values %s. Reset to default %d",
                                flagId, enumName, def.enumValues(), def.defaultValue());
                        }
                    } else { // INT
                        if (valNode.isNumber()) {
                            int candidate = valNode.asInt();
                            if (candidate >= def.min() && candidate <= def.max()) {
                                validatedVal = candidate;
                            } else {
                                Log.warn(LogCategory.DATA, "SAVE_WARN_FLAG_OUT_OF_RANGE: Flag '%s' value %d outside [%d..%d]. Reset to default %d",
                                    flagId, candidate, def.min(), def.max(), def.defaultValue());
                            }
                        } else {
                            Log.warn(LogCategory.DATA, "SAVE_WARN_FLAG_OUT_OF_RANGE: Flag '%s' expected integer, got %s. Reset to default %d",
                                flagId, valNode, def.defaultValue());
                        }
                    }
                    globalFlags.put(flagId, validatedVal);
                }

                // Check for missing global flags from registry -> populate default
                for (FlagDefinition def : flagRegistry.getAll()) {
                    if (def.scope() == FlagScope.GLOBAL && !globalFlags.containsKey(def.id())) {
                        globalFlags.put(def.id(), def.defaultValue());
                    }
                }
            }

            // 5. Validate Room Records (including room-scoped flags)
            Map<String, Map<String, Object>> roomRecords = new LinkedHashMap<>();
            JsonNode roomsNode = dataNode.get("roomRecords");
            if (roomsNode != null && roomsNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> roomFields = roomsNode.fields();
                while (roomFields.hasNext()) {
                    Map.Entry<String, JsonNode> rEntry = roomFields.next();
                    String roomId = rEntry.getKey();
                    JsonNode rData = rEntry.getValue();
                    if (!rData.isObject()) continue;

                    Map<String, Object> validEntries = new LinkedHashMap<>();
                    Iterator<Map.Entry<String, JsonNode>> entryFields = rData.fields();
                    while (entryFields.hasNext()) {
                        Map.Entry<String, JsonNode> eField = entryFields.next();
                        String entryKey = eField.getKey();
                        JsonNode eValNode = eField.getValue();

                        // Check if key is a room-scoped flag (flag.<id>)
                        if (entryKey.startsWith("flag.") && flagRegistry != null) {
                            String flagId = entryKey.substring(5);
                            FlagDefinition def = flagRegistry.get(flagId);
                            if (def == null || def.scope() != FlagScope.ROOM) {
                                Log.warn(LogCategory.DATA, "SAVE_WARN_UNKNOWN_ROOM_FLAG: Dropping unknown room-scoped flag '%s' in room '%s'", flagId, roomId);
                                continue;
                            }

                            // Extract value
                            int val = def.defaultValue();
                            JsonNode rawNode = eValNode.has("value") ? eValNode.get("value") : eValNode;
                            if (def.type() == FlagType.BOOL) {
                                if (rawNode.isBoolean()) {
                                    val = rawNode.asBoolean() ? 1 : 0;
                                } else if (rawNode.isNumber() && (rawNode.asInt() == 0 || rawNode.asInt() == 1)) {
                                    val = rawNode.asInt();
                                } else {
                                    Log.warn(LogCategory.DATA, "SAVE_WARN_ROOM_FLAG_OUT_OF_RANGE: Room flag '%s' in room '%s' expected boolean, got %s. Reset to default %d",
                                        flagId, roomId, rawNode, def.defaultValue());
                                }
                            } else if (def.type() == FlagType.INT) {
                                if (rawNode.isNumber()) {
                                    int n = rawNode.asInt();
                                    if (n >= def.min() && n <= def.max()) {
                                        val = n;
                                    } else {
                                        Log.warn(LogCategory.DATA, "SAVE_WARN_ROOM_FLAG_OUT_OF_RANGE: Room flag '%s' in room '%s' value %d outside bounds [%d..%d]. Reset to default %d",
                                            flagId, roomId, n, def.min(), def.max(), def.defaultValue());
                                    }
                                } else {
                                    Log.warn(LogCategory.DATA, "SAVE_WARN_ROOM_FLAG_OUT_OF_RANGE: Room flag '%s' in room '%s' expected integer, got %s. Reset to default %d",
                                        flagId, roomId, rawNode, def.defaultValue());
                                }
                            }

                            validEntries.put(entryKey, Map.of("type", "INT", "value", val));
                        } else {
                            // Non-flag room record entry: preserve as map or primitive
                            if (eValNode.isObject()) {
                                validEntries.put(entryKey, MAPPER.convertValue(eValNode, Map.class));
                            } else if (eValNode.isNumber()) {
                                validEntries.put(entryKey, eValNode.asInt());
                            } else {
                                validEntries.put(entryKey, eValNode.asText());
                            }
                        }
                    }
                    roomRecords.put(roomId, validEntries);
                }
            }

            // 6. Unknown / Reserved Sections preservation
            Map<String, Object> reservedSections = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> dataFields = dataNode.fields();
            while (dataFields.hasNext()) {
                Map.Entry<String, JsonNode> df = dataFields.next();
                String fieldName = df.getKey();
                if (!KNOWN_DATA_FIELDS.contains(fieldName)) {
                    Log.warn(LogCategory.DATA, "SAVE_WARN_RESERVED_SECTION: Preserving unknown/reserved section '%s'", fieldName);
                    reservedSections.put(fieldName, df.getValue());
                }
            }

            SavePayload payload = new SavePayload(
                playtimeTicks,
                timestampUtc,
                currentRoomId,
                savepointInstanceId,
                globalFlags,
                roomRecords,
                reservedSections
            );

            return new SaveEnvelope(version, gameVersion, declaredChecksum, payload);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("SAVE_ERR_MALFORMED_JSON: Failed to parse save file JSON: " + e.getMessage(), e);
        }
    }
}
