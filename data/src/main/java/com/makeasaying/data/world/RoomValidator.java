package com.makeasaying.data.world;

import com.makeasaying.engine.entity.TileShape;
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
        if (data.entities() != null) {
            for (RoomLoader.EntityInstanceData ed : data.entities()) {
                String eId = ed.instanceId();
                if (eId == null || !ID_PATTERN.matcher(eId).matches()) {
                    findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ERR_INVALID_ID",
                        "room " + roomId + ", entity " + eId, "Invalid entity instanceId '" + eId + "': must match [a-z0-9_.]+"));
                } else if (!seenEntityIds.add(eId)) {
                    findings.add(new ValidationFinding(ValidationSeverity.ERROR, "ERR_DUPLICATE_ENTITY_ID",
                        "room " + roomId, "Duplicate entity instance ID '" + eId + "'"));
                }
            }
        }

        return findings;
    }

    private static String findTilesetId(List<RoomLoader.TilesetAliasData> aliases, String alias) {
        if (aliases == null) return null;
        for (RoomLoader.TilesetAliasData tad : aliases) {
            if (alias.equals(tad.alias())) return tad.id();
        }
        return null;
    }
}
