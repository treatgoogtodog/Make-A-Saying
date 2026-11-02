package com.makeasaying.data.world;

import com.makeasaying.engine.entity.EntityDefinition;
import com.makeasaying.engine.entity.TileShape;
import com.makeasaying.engine.schema.ComponentField;
import com.makeasaying.engine.schema.ComponentSchema;
import com.makeasaying.engine.schema.ComponentSchemaRegistry;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomEntityData;
import com.makeasaying.engine.world.RoomLayer;
import com.makeasaying.engine.world.RoomLayerType;
import com.makeasaying.engine.world.SpawnPoint;
import com.makeasaying.engine.world.TileDef;
import com.makeasaying.engine.world.Tileset;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates Room files and their referenced Tilesets per M5a brief.
 * Returns structured findings: severity, stable code, location, message.
 */
public final class RoomValidator {
    public static final int DEFAULT_WARN_WIDTH = 64;
    public static final int DEFAULT_WARN_HEIGHT = 48;
    public static final int REQUIRED_TILE_SIZE = 16;

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_.]+$");
    private static final Pattern ALIAS_PATTERN = Pattern.compile("^[a-z]{1,2}$");

    private final int warnWidth;
    private final int warnHeight;
    private final int requiredTileSize;

    public RoomValidator(int warnWidth, int warnHeight, int requiredTileSize) {
        this.warnWidth = warnWidth;
        this.warnHeight = warnHeight;
        this.requiredTileSize = requiredTileSize;
    }

    public RoomValidator() {
        this(DEFAULT_WARN_WIDTH, DEFAULT_WARN_HEIGHT, REQUIRED_TILE_SIZE);
    }

    public List<ValidationFinding> validate(RoomLoader.RoomData data, Map<String, Tileset> loadedTilesets) {
        return validate(data, loadedTilesets, null, null);
    }

    public List<ValidationFinding> validate(
        RoomLoader.RoomData data,
        Map<String, Tileset> loadedTilesets,
        Map<String, EntityDefinition> entityDefs,
        ComponentSchemaRegistry schemaRegistry
    ) {
        return validate(data, loadedTilesets, entityDefs, schemaRegistry, null, null);
    }

    public List<ValidationFinding> validate(
        RoomLoader.RoomData data,
        Map<String, Tileset> loadedTilesets,
        Map<String, EntityDefinition> entityDefs,
        ComponentSchemaRegistry schemaRegistry,
        com.makeasaying.engine.flags.FlagRegistry flagRegistry,
        com.makeasaying.engine.condition.AtomRegistry atomRegistry
    ) {
        List<ValidationFinding> findings = new ArrayList<>();
        String roomId = data.id();

        // 1. Room ID validation
        if (roomId == null || roomId.isBlank()) {
            findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ERR_INVALID_ID",
                "room", "Room ID must not be blank"));
        } else if (!ID_PATTERN.matcher(roomId).matches()) {
            findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ERR_INVALID_ID",
                "room " + roomId, "Invalid room ID '" + roomId + "': must match [a-z0-9_.]+"));
        }

        // 2. Room size warning (Open Decision 6)
        if (data.width() > warnWidth || data.height() > warnHeight) {
            findings.add(new ValidationFinding(ValidationSeverity.WARNING, "WARN_ROOM_SIZE",
                "room " + roomId, String.format("Room dimensions %dx%d exceed recommended warning size %dx%d",
                    data.width(), data.height(), warnWidth, warnHeight)));
        }

        // 3. Tileset Aliases Validation
        Set<String> seenAliases = new HashSet<>();
        if (data.tilesets() != null) {
            for (RoomLoader.TilesetAliasData tad : data.tilesets()) {
                String alias = tad.alias();
                String tsId = tad.id();

                if (alias == null || !ALIAS_PATTERN.matcher(alias).matches()) {
                    findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ERR_INVALID_ALIAS",
                        "room " + roomId, "Invalid tileset alias '" + alias + "': must be 1 to 2 lowercase letters [a-z]"));
                } else if (!seenAliases.add(alias)) {
                    findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ERR_DUPLICATE_ALIAS",
                        "room " + roomId, "Duplicate tileset alias '" + alias + "'"));
                }

                if (tsId == null || !ID_PATTERN.matcher(tsId).matches()) {
                    findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ERR_INVALID_ID",
                        "room " + roomId, "Invalid tileset ID '" + tsId + "' for alias '" + alias + "': must match [a-z0-9_.]+"));
                } else if (loadedTilesets != null && !loadedTilesets.containsKey(tsId)) {
                    findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ERR_UNKNOWN_TILESET",
                        "room " + roomId, "Unknown tileset ID '" + tsId + "' referenced by alias '" + alias + "'"));
                } else if (loadedTilesets != null) {
                    Tileset ts = loadedTilesets.get(tsId);
                    if (ts != null && ts.tileSize() != requiredTileSize) {
                        findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ERR_TILE_SIZE_MISMATCH",
                            "room " + roomId + ", tileset " + tsId,
                            String.format("Tileset tileSize %d does not match config %d", ts.tileSize(), requiredTileSize)));
                    }
                }
            }
        }

        // 4. Layers Validation
        Map<String, List<String>> layers = data.layers();
        if (layers == null || !layers.containsKey("main")) {
            findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ERR_MISSING_MAIN_LAYER",
                "room " + roomId, "Missing required layer 'main'"));
        }

        if (layers != null) {
            for (Map.Entry<String, List<String>> entry : layers.entrySet()) {
                String layerName = entry.getKey();
                List<String> rows = entry.getValue();

                if (rows == null || rows.size() != data.height()) {
                    int rowCount = (rows != null) ? rows.size() : 0;
                    findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ERR_ROW_COUNT_MISMATCH",
                        "room " + roomId + ", layer " + layerName,
                        String.format("Layer row count %d does not match room height %d", rowCount, data.height())));
                }

                if (rows != null) {
                    for (int r = 0; r < rows.size(); r++) {
                        String rowStr = rows.get(r);
                        String[] tokens = (rowStr != null && !rowStr.trim().isEmpty()) ? rowStr.trim().split("\\s+") : new String[0];

                        if (tokens.length != data.width()) {
                            findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ERR_TOKEN_COUNT_MISMATCH",
                                String.format("room %s, layer %s, row %d", roomId, layerName, r),
                                String.format("Token count %d does not match room width %d", tokens.length, data.width())));
                        }

                        // Validate individual tokens
                        for (int c = 0; c < tokens.length; c++) {
                            String token = tokens[c];
                            if (token.equals(".")) continue;

                            String[] parts = token.split(":", 2);
                            if (parts.length != 2) {
                                findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ERR_INVALID_TOKEN",
                                    String.format("room %s, layer %s, row %d, col %d", roomId, layerName, r, c),
                                    "Invalid token format '" + token + "': expected '.' or 'alias:tileId'"));
                                continue;
                            }

                            String alias = parts[0];
                            String tileId = parts[1];

                            // Check alias registered in room
                            boolean aliasFound = false;
                            String targetTsId = null;
                            if (data.tilesets() != null) {
                                for (RoomLoader.TilesetAliasData tad : data.tilesets()) {
                                    if (alias.equals(tad.alias())) {
                                        aliasFound = true;
                                        targetTsId = tad.id();
                                        break;
                                    }
                                }
                            }

                            if (!aliasFound) {
                                findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ERR_UNKNOWN_ALIAS",
                                    String.format("room %s, layer %s, row %d, col %d", roomId, layerName, r, c),
                                    "Unknown tileset alias '" + alias + "'"));
                            } else if (loadedTilesets != null && targetTsId != null && loadedTilesets.containsKey(targetTsId)) {
                                Tileset ts = loadedTilesets.get(targetTsId);
                                if (ts != null && ts.getTile(tileId) == null) {
                                    findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ERR_UNKNOWN_TILE",
                                        String.format("room %s, layer %s, row %d, col %d", roomId, layerName, r, c),
                                        String.format("Unknown tile '%s' in tileset '%s'", tileId, alias)));
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Spawn Points Validation
        Set<String> seenSpawnIds = new HashSet<>();
        if (data.spawnPoints() != null) {
            for (RoomLoader.SpawnPointData sp : data.spawnPoints()) {
                String spId = sp.id();
                if (spId == null || !ID_PATTERN.matcher(spId).matches()) {
                    findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ERR_INVALID_ID",
                        "room " + roomId + ", spawnPoint " + spId, "Invalid spawnPoint ID '" + spId + "': must match [a-z0-9_.]+"));
                } else if (!seenSpawnIds.add(spId)) {
                    findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ERR_DUPLICATE_SPAWN_ID",
                        "room " + roomId, "Duplicate spawn point ID '" + spId + "'"));
                }

                int roomPxW = data.width() * requiredTileSize;
                int roomPxH = data.height() * requiredTileSize;

                if (sp.x() < 0 || sp.x() >= roomPxW || sp.y() < 0 || sp.y() >= roomPxH) {
                    findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ERR_SPAWN_OUT_OF_BOUNDS",
                        "room " + roomId + ", spawnPoint " + spId,
                        String.format("Spawn point (%d, %d) is out of room bounds (0..%d, 0..%d)",
                            sp.x(), sp.y(), roomPxW - 1, roomPxH - 1)));
                } else if (layers != null && layers.containsKey("main") && loadedTilesets != null) {
                    // Check if spawn point feet is inside a solid tile
                    int tileX = sp.x() / requiredTileSize;
                    // Y in json is inverted
                    int jsonRow = data.height() - 1 - (sp.y() / requiredTileSize);
                    List<String> mainRows = layers.get("main");
                    if (mainRows != null && jsonRow >= 0 && jsonRow < mainRows.size()) {
                        String rowStr = mainRows.get(jsonRow);
                        String[] tokens = (rowStr != null && !rowStr.trim().isEmpty()) ? rowStr.trim().split("\\s+") : new String[0];
                        if (tileX >= 0 && tileX < tokens.length) {
                            String token = tokens[tileX];
                            if (!token.equals(".")) {
                                String[] parts = token.split(":", 2);
                                if (parts.length == 2) {
                                    String alias = parts[0];
                                    String tileId = parts[1];
                                    String targetTsId = findTilesetId(data.tilesets(), alias);
                                    if (targetTsId != null && loadedTilesets.containsKey(targetTsId)) {
                                        Tileset ts = loadedTilesets.get(targetTsId);
                                        TileDef def = (ts != null) ? ts.getTile(tileId) : null;
                                        if (def != null && def.shape() == TileShape.SOLID) {
                                            findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ERR_SPAWN_INSIDE_SOLID",
                                                "room " + roomId + ", spawnPoint " + spId,
                                                String.format("Spawn point (%d, %d) is inside a solid tile '%s' at tile (%d, %d)",
                                                    sp.x(), sp.y(), token, tileX, sp.y() / requiredTileSize)));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Entity Instances Validation
        Set<String> seenEntityIds = new HashSet<>();
        int roomPxW = data.width() * requiredTileSize;
        int roomPxH = data.height() * requiredTileSize;
        int savepointCount = 0;

        if (data.entities() != null) {
            for (RoomLoader.EntityInstanceData ed : data.entities()) {
                String eId = ed.instanceId();
                String loc = String.format("room %s, entity %s (%d, %d)", roomId, eId, ed.x(), ed.y());

                if (eId == null || !ID_PATTERN.matcher(eId).matches()) {
                    findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ROOM_ERR_INVALID_ID",
                        loc, "Invalid entity instanceId '" + eId + "': must match [a-z0-9_.]+"));
                } else if (!seenEntityIds.add(eId)) {
                    findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ROOM_ERR_DUPLICATE_INSTANCE_ID",
                        loc, "Duplicate entity instance ID '" + eId + "'"));
                }

                // Check suffix against entitySeq
                if (eId != null) {
                    int lastUnderscore = eId.lastIndexOf('_');
                    if (lastUnderscore >= 0 && lastUnderscore < eId.length() - 1) {
                        try {
                            int suffix = Integer.parseInt(eId.substring(lastUnderscore + 1));
                            if (suffix >= data.entitySeq()) {
                                findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ROOM_ERR_ENTITY_SEQ_INVALID",
                                    "room " + roomId,
                                    String.format("entitySeq %d must be strictly greater than instanceId '%s' numeric suffix %d",
                                        data.entitySeq(), eId, suffix)));
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }

                // Out of bounds
                if (ed.x() < 0 || ed.x() >= roomPxW || ed.y() < 0 || ed.y() >= roomPxH) {
                    findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ROOM_ERR_ENTITY_OUT_OF_BOUNDS",
                        loc, String.format("Entity instance '%s' at (%d, %d) is out of room bounds (0..%d, 0..%d)",
                            eId, ed.x(), ed.y(), roomPxW - 1, roomPxH - 1)));
                }

                EntityDefinition def = (entityDefs != null && ed.definition() != null) ? entityDefs.get(ed.definition()) : null;
                if (entityDefs != null) {
                    if (def == null) {
                        findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ROOM_ERR_UNKNOWN_DEF",
                            loc, "Unknown entity definition '" + ed.definition() + "'"));
                    } else if (!def.placeable()) {
                        findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ROOM_ERR_NON_PLACEABLE_DEF",
                            loc, "Entity definition '" + ed.definition() + "' is marked placeable: false"));
                    }
                }

                // Overrides validation
                if (ed.overrides() != null && schemaRegistry != null) {
                    for (Map.Entry<String, Map<String, Object>> compEntry : ed.overrides().entrySet()) {
                        String compId = compEntry.getKey();
                        ComponentSchema schema = schemaRegistry.getSchema(compId);
                        if (schema == null) {
                            findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ROOM_ERR_UNKNOWN_COMPONENT",
                                loc, "Unknown component '" + compId + "' in overrides"));
                        } else {
                            Map<String, Object> fieldOverrides = compEntry.getValue();
                            if (fieldOverrides != null) {
                                if (compId.equals("platform")) {
                                    if (fieldOverrides.containsKey("startX") || fieldOverrides.containsKey("startY") ||
                                        fieldOverrides.containsKey("endX") || fieldOverrides.containsKey("endY")) {
                                        findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ROOM_ERR_LEGACY_PLATFORM_COORDINATES",
                                            loc, "Entity overrides contain obsolete absolute coordinates (startX, startY, endX, endY); platform motion must use relative offsets travelX and travelY"));
                                    }
                                }
                                for (Map.Entry<String, Object> fEntry : fieldOverrides.entrySet()) {
                                    String fieldName = fEntry.getKey();
                                    Object val = fEntry.getValue();
                                    ComponentField field = schema.getField(fieldName);
                                    if (field == null) {
                                        findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ROOM_ERR_UNKNOWN_FIELD",
                                            loc, "Unknown field '" + fieldName + "' for component '" + compId + "'"));
                                    } else if (!field.overridable()) {
                                        findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ROOM_ERR_NON_OVERRIDABLE_FIELD",
                                            loc, "Field '" + fieldName + "' on component '" + compId + "' is not overridable"));
                                    } else if (!field.isValidValue(val)) {
                                        findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ROOM_ERR_OVERRIDE_OUT_OF_RANGE",
                                            loc, String.format("Override value '%s' for field '%s' on component '%s' is out of range or invalid type",
                                                val, fieldName, compId)));
                                    }
                                }
                            }
                        }
                    }
                }

                // Warnings: solid inside solid and platform crosses solid
                if (def != null && layers != null && layers.containsKey("main") && loadedTilesets != null) {
                    // Check solid overlap
                    if (def.tags() != null && def.tags().contains("solid")) {
                        int colW = 16, colH = 16, colOx = -8, colOy = 0;
                        Map<String, Object> colMap = def.components().get("collider");
                        if (colMap != null) {
                            colW = ((Number) colMap.getOrDefault("width", 16)).intValue();
                            colH = ((Number) colMap.getOrDefault("height", 16)).intValue();
                            colOx = ((Number) colMap.getOrDefault("offsetX", -colW / 2)).intValue();
                            colOy = ((Number) colMap.getOrDefault("offsetY", 0)).intValue();
                        }
                        if (ed.overrides() != null && ed.overrides().containsKey("collider")) {
                            Map<String, Object> ovrCol = ed.overrides().get("collider");
                            if (ovrCol.containsKey("width")) colW = ((Number) ovrCol.get("width")).intValue();
                            if (ovrCol.containsKey("height")) colH = ((Number) ovrCol.get("height")).intValue();
                            if (ovrCol.containsKey("offsetX")) colOx = ((Number) ovrCol.get("offsetX")).intValue();
                            if (ovrCol.containsKey("offsetY")) colOy = ((Number) ovrCol.get("offsetY")).intValue();
                        }
                        if (isSolidAtBox(ed.x() + colOx, ed.y() + colOy, colW, colH, data, layers.get("main"), loadedTilesets)) {
                            findings.add(new ValidationFinding(ValidationSeverity.WARNING, "ROOM_WARN_SOLID_INSIDE_SOLID",
                                loc, "Entity '" + eId + "' solid collider overlaps solid tile terrain"));
                        }
                    }

                    // Check savepoint
                    if (def.components().containsKey("savepoint") || (def.tags() != null && def.tags().contains("savepoint"))) {
                        savepointCount++;
                        int colW = 16, colH = 24, colOx = -8, colOy = 0;
                        Map<String, Object> colMap = def.components().get("collider");
                        if (colMap != null) {
                            colW = ((Number) colMap.getOrDefault("width", 16)).intValue();
                            colH = ((Number) colMap.getOrDefault("height", 24)).intValue();
                            colOx = ((Number) colMap.getOrDefault("offsetX", -colW / 2)).intValue();
                            colOy = ((Number) colMap.getOrDefault("offsetY", 0)).intValue();
                        }
                        if (isSolidAtBox(ed.x() + colOx, ed.y() + colOy, colW, colH, data, layers.get("main"), loadedTilesets)) {
                            findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ROOM_ERR_SAVEPOINT_INSIDE_SOLID",
                                loc, "Savepoint '" + eId + "' trigger box overlaps solid tile terrain"));
                        }
                    }

                    // Check platform trajectory
                    if (def.components().containsKey("platform")) {
                        int tx = 0, ty = 0;
                        Map<String, Object> platMap = def.components().get("platform");
                        if (platMap != null) {
                            tx = ((Number) platMap.getOrDefault("travelX", 0)).intValue();
                            ty = ((Number) platMap.getOrDefault("travelY", 0)).intValue();
                        }
                        if (ed.overrides() != null && ed.overrides().containsKey("platform")) {
                            Map<String, Object> ovrPlat = ed.overrides().get("platform");
                            if (ovrPlat.containsKey("travelX")) tx = ((Number) ovrPlat.get("travelX")).intValue();
                            if (ovrPlat.containsKey("travelY")) ty = ((Number) ovrPlat.get("travelY")).intValue();
                        }
                        int endX = ed.x() + tx;
                        int endY = ed.y() + ty;
                        if (endX < 0 || endX >= roomPxW || endY < 0 || endY >= roomPxH ||
                            isSolidAtPoint(endX, endY, data, layers.get("main"), loadedTilesets)) {
                            findings.add(new ValidationFinding(ValidationSeverity.WARNING, "ROOM_WARN_PLATFORM_CROSSES_SOLID",
                                loc, "Platform '" + eId + "' travel trajectory leaves room bounds or intersects solid tiles"));
                        }
                    }
                }
                // Condition validation for entity
                if (ed.spawnCondition() != null && flagRegistry != null) {
                    try {
                        com.makeasaying.data.condition.ConditionLoader.parse(
                            ed.spawnCondition(), flagRegistry, atomRegistry != null ? atomRegistry : new com.makeasaying.engine.condition.AtomRegistry());
                    } catch (IllegalArgumentException e) {
                        String msg = e.getMessage();
                        String code = "COND_ERR_INVALID_CONDITION";
                        if (msg != null && msg.contains(":")) {
                            String[] parts = msg.split(":", 2);
                            code = parts[0].trim();
                            msg = parts[1].trim();
                        }
                        findings.add(new ValidationFinding(ValidationSeverity.ERROR, code, loc, msg));
                    }
                }
            }
        }

        if (savepointCount == 0) {
            findings.add(new ValidationFinding(ValidationSeverity.INFO, "ROOM_INFO_NO_SAVEPOINT",
                "room " + roomId, "Room has no savepoint entity"));
        }

        // 7. Exits validation
        if (data.exits() != null) {
            for (RoomLoader.RoomExitData exit : data.exits()) {
                String exLoc = "room " + roomId + ", exit " + exit.id();
                if (exit.condition() != null && flagRegistry != null) {
                    try {
                        com.makeasaying.data.condition.ConditionLoader.parse(
                            exit.condition(), flagRegistry, atomRegistry != null ? atomRegistry : new com.makeasaying.engine.condition.AtomRegistry());
                    } catch (IllegalArgumentException e) {
                        String msg = e.getMessage();
                        String code = "COND_ERR_INVALID_CONDITION";
                        if (msg != null && msg.contains(":")) {
                            String[] parts = msg.split(":", 2);
                            code = parts[0].trim();
                            msg = parts[1].trim();
                        }
                        findings.add(new ValidationFinding(ValidationSeverity.ERROR, code, exLoc, msg));
                    }
                }
            }
        }

        return findings;
    }

    private static boolean isSolidAtPoint(int px, int py, RoomLoader.RoomData data, List<String> mainRows, Map<String, Tileset> loadedTilesets) {
        if (mainRows == null || data == null) return false;
        int tileX = px / 16;
        int tileY = py / 16;
        int jsonRow = data.height() - 1 - tileY;
        if (jsonRow < 0 || jsonRow >= mainRows.size()) return false;
        String rowStr = mainRows.get(jsonRow);
        String[] tokens = (rowStr != null && !rowStr.trim().isEmpty()) ? rowStr.trim().split("\\s+") : new String[0];
        if (tileX < 0 || tileX >= tokens.length) return false;
        String token = tokens[tileX];
        if (".".equals(token)) return false;
        String[] parts = token.split(":", 2);
        if (parts.length == 2) {
            String alias = parts[0];
            String tileId = parts[1];
            String targetTsId = findTilesetId(data.tilesets(), alias);
            if (targetTsId != null && loadedTilesets.containsKey(targetTsId)) {
                Tileset ts = loadedTilesets.get(targetTsId);
                TileDef def = (ts != null) ? ts.getTile(tileId) : null;
                return def != null && def.shape() == TileShape.SOLID;
            }
        }
        return false;
    }

    private static boolean isSolidAtBox(int x, int y, int w, int h, RoomLoader.RoomData data, List<String> mainRows, Map<String, Tileset> loadedTilesets) {
        int minTileX = x / 16;
        int maxTileX = (x + w - 1) / 16;
        int minTileY = y / 16;
        int maxTileY = (y + h - 1) / 16;
        for (int ty = minTileY; ty <= maxTileY; ty++) {
            for (int tx = minTileX; tx <= maxTileX; tx++) {
                if (isSolidAtPoint(tx * 16 + 8, ty * 16 + 8, data, mainRows, loadedTilesets)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String findTilesetId(List<RoomLoader.TilesetAliasData> aliases, String alias) {
        if (aliases == null) return null;
        for (RoomLoader.TilesetAliasData tad : aliases) {
            if (alias.equals(tad.alias())) return tad.id();
        }
        return null;
    }
}
