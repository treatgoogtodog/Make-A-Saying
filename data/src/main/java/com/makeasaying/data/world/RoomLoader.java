package com.makeasaying.data.world;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomEntityData;
import com.makeasaying.engine.world.RoomLayer;
import com.makeasaying.engine.world.RoomLayerType;
import com.makeasaying.engine.world.SpawnPoint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads Room files from content/rooms/<id>.json per M5a brief.
 * Flips row order from top-to-bottom in file to Y-up in runtime memory (DEC-007).
 */
public final class RoomLoader {
    public static final int CURRENT_VERSION = 3;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final MigrationChain MIGRATION_CHAIN = new MigrationChain("room", CURRENT_VERSION);

    static {
        // v1 -> v2: Add empty "exits" array if not present
        MIGRATION_CHAIN.register(1, root -> {
            if (!root.has("exits")) {
                root.putArray("exits");
            }
        });

        // v2 -> v3: Add entitySeq and convert flat overrides to component-nested overrides
        MIGRATION_CHAIN.register(2, root -> {
            if (!root.has("entitySeq")) {
                int max = 0;
                if (root.has("entities") && root.get("entities").isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode entityNode : root.get("entities")) {
                        if (entityNode.has("instanceId") && entityNode.get("instanceId").isTextual()) {
                            String iid = entityNode.get("instanceId").asText();
                            int idx = iid.lastIndexOf('_');
                            if (idx >= 0 && idx < iid.length() - 1) {
                                try {
                                    int val = Integer.parseInt(iid.substring(idx + 1));
                                    if (val > max) max = val;
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }
                root.put("entitySeq", max + 1);
            }

            if (root.has("entities") && root.get("entities").isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode entityNode : root.get("entities")) {
                    if (entityNode instanceof com.fasterxml.jackson.databind.node.ObjectNode objEntity) {
                        if (objEntity.has("overrides") && objEntity.get("overrides").isObject()) {
                            com.fasterxml.jackson.databind.node.ObjectNode overridesNode = (com.fasterxml.jackson.databind.node.ObjectNode) objEntity.get("overrides");
                            boolean isFlat = false;
                            java.util.Iterator<String> fieldNames = overridesNode.fieldNames();
                            while (fieldNames.hasNext()) {
                                String field = fieldNames.next();
                                if (!overridesNode.get(field).isObject()) {
                                    isFlat = true;
                                    break;
                                }
                            }
                            if (isFlat) {
                                com.fasterxml.jackson.databind.node.ObjectNode structured = objEntity.objectNode();
                                com.fasterxml.jackson.databind.node.ObjectNode platformObj = objEntity.objectNode();
                                java.util.Iterator<Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> fields = overridesNode.fields();
                                while (fields.hasNext()) {
                                    Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> fieldEntry = fields.next();
                                    if (fieldEntry.getValue().isObject()) {
                                        structured.set(fieldEntry.getKey(), fieldEntry.getValue());
                                    } else {
                                        platformObj.set(fieldEntry.getKey(), fieldEntry.getValue());
                                    }
                                }
                                if (structured.has("platform") && structured.get("platform").isObject()) {
                                    com.fasterxml.jackson.databind.node.ObjectNode pNode =
                                        (com.fasterxml.jackson.databind.node.ObjectNode) structured.get("platform");
                                    convertLegacyPlatformCoords(pNode, objEntity);
                                }
                                if (!platformObj.isEmpty()) {
                                    convertLegacyPlatformCoords(platformObj, objEntity);
                                    structured.set("platform", platformObj);
                                }
                                objEntity.set("overrides", structured);
                            }
                        }
                    }
                }
            }
        });
    }

    private static void convertLegacyPlatformCoords(
        com.fasterxml.jackson.databind.node.ObjectNode platformObj,
        com.fasterxml.jackson.databind.node.ObjectNode objEntity
    ) {
        int entityX = objEntity.has("x") ? objEntity.get("x").asInt() : 0;
        int entityY = objEntity.has("y") ? objEntity.get("y").asInt() : 0;
        if (platformObj.has("endX") && !platformObj.has("travelX")) {
            platformObj.put("travelX", platformObj.get("endX").asInt() - entityX);
        }
        if (platformObj.has("endY") && !platformObj.has("travelY")) {
            platformObj.put("travelY", platformObj.get("endY").asInt() - entityY);
        }
        platformObj.remove("startX");
        platformObj.remove("startY");
        platformObj.remove("endX");
        platformObj.remove("endY");
    }

    private RoomLoader() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"version", "id", "width", "height", "entitySeq", "tilesets", "layers", "spawnPoints", "entities", "exits"})
    public record RoomData(
        int version,
        String id,
        int width,
        int height,
        int entitySeq,
        List<TilesetAliasData> tilesets,
        Map<String, List<String>> layers,
        List<SpawnPointData> spawnPoints,
        List<EntityInstanceData> entities,
        List<RoomExitData> exits
    ) {
        public RoomData(
            int version,
            String id,
            int width,
            int height,
            List<TilesetAliasData> tilesets,
            Map<String, List<String>> layers,
            List<SpawnPointData> spawnPoints,
            List<EntityInstanceData> entities,
            List<RoomExitData> exits
        ) {
            this(version, id, width, height, computeDefaultSeq(entities), tilesetAliases(tilesets), layers, spawnPoints, entities, exits);
        }

        public RoomData(
            int version,
            String id,
            int width,
            int height,
            List<TilesetAliasData> tilesets,
            Map<String, List<String>> layers,
            List<SpawnPointData> spawnPoints,
            List<EntityInstanceData> entities
        ) {
            this(version, id, width, height, computeDefaultSeq(entities), tilesets, layers, spawnPoints, entities, Collections.emptyList());
        }

        private static List<TilesetAliasData> tilesetAliases(List<TilesetAliasData> tilesets) {
            return tilesets;
        }

        private static int computeDefaultSeq(List<EntityInstanceData> entities) {
            int max = 0;
            if (entities != null) {
                for (EntityInstanceData ed : entities) {
                    if (ed == null || ed.instanceId() == null) continue;
                    String iid = ed.instanceId();
                    int idx = iid.lastIndexOf('_');
                    if (idx >= 0 && idx < iid.length() - 1) {
                        try {
                            int val = Integer.parseInt(iid.substring(idx + 1));
                            if (val > max) max = val;
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            return max + 1;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"alias", "id"})
    public record TilesetAliasData(String alias, String id) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"id", "x", "y"})
    public record SpawnPointData(String id, int x, int y) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"id", "x", "y", "width", "height", "targetRoom", "targetSpawn", "condition"})
    public record RoomExitData(
        String id,
        int x,
        int y,
        @com.fasterxml.jackson.annotation.JsonProperty("width") @com.fasterxml.jackson.annotation.JsonAlias({"w"}) int width,
        @com.fasterxml.jackson.annotation.JsonProperty("height") @com.fasterxml.jackson.annotation.JsonAlias({"h"}) int height,
        String targetRoom,
        String targetSpawn,
        com.fasterxml.jackson.databind.JsonNode condition
    ) {
        public RoomExitData(
            String id,
            int x,
            int y,
            int width,
            int height,
            String targetRoom,
            String targetSpawn
        ) {
            this(id, x, y, width, height, targetRoom, targetSpawn, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"instanceId", "definition", "x", "y", "overrides", "spawnCondition"})
    public record EntityInstanceData(
        String instanceId,
        String definition,
        int x,
        int y,
        Map<String, Map<String, Object>> overrides,
        com.fasterxml.jackson.databind.JsonNode spawnCondition
    ) {
        public EntityInstanceData(
            String instanceId,
            String definition,
            int x,
            int y,
            Map<String, Map<String, Object>> overrides
        ) {
            this(instanceId, definition, x, y, overrides, null);
        }
    }

    public static Room load(Path path) throws IOException {
        return load(path, null, null);
    }

    public static RoomData loadData(Path path) throws IOException {
        if (!Files.exists(path)) {
            Log.error(LogCategory.DATA, "Room file not found: %s", path.toAbsolutePath());
            throw new IllegalArgumentException("Room file not found: " + path);
        }

        try {
            com.fasterxml.jackson.databind.JsonNode rootNode = MAPPER.readTree(path.toFile());
            if (rootNode instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
                MIGRATION_CHAIN.migrate(objectNode);
                return MAPPER.treeToValue(objectNode, RoomData.class);
            } else {
                throw new IOException("Root of room file must be a JSON object: " + path);
            }
        } catch (Exception e) {
            Log.error(LogCategory.DATA, "Failed to parse room file: %s", e, path);
            if (e instanceof IOException ioException) throw ioException;
            if (e instanceof IllegalArgumentException iae) throw iae;
            throw new IOException("Malformed room file: " + path, e);
        }
    }

    public static Room load(
        Path path,
        com.makeasaying.engine.flags.FlagRegistry flagRegistry,
        com.makeasaying.engine.condition.AtomRegistry atomRegistry
    ) throws IOException {
        RoomData data = loadData(path);
        return fromData(data, flagRegistry, atomRegistry);
    }

    public static Room fromData(RoomData data) {
        return fromData(data, null, null);
    }

    public static Room fromData(
        RoomData data,
        com.makeasaying.engine.flags.FlagRegistry flagRegistry,
        com.makeasaying.engine.condition.AtomRegistry atomRegistry
    ) {
        if (data.version() > CURRENT_VERSION) {
            throw new IllegalArgumentException("Unknown newer room version: " + data.version());
        }

        Map<String, String> aliasMap = new LinkedHashMap<>();
        if (data.tilesets() != null) {
            for (TilesetAliasData tad : data.tilesets()) {
                aliasMap.put(tad.alias(), tad.id());
            }
        }

        int width = data.width();
        int height = data.height();

        Map<RoomLayerType, RoomLayer> layerMap = new LinkedHashMap<>();
        if (data.layers() != null) {
            for (Map.Entry<String, List<String>> entry : data.layers().entrySet()) {
                RoomLayerType layerType = RoomLayerType.fromId(entry.getKey());
                if (layerType == null) continue;

                List<String> rows = entry.getValue();
                String[] tokens = new String[width * height];

                // Flip row order to Y-up (DEC-007)
                // Row 0 in JSON is top of room (y = height - 1)
                // Row height - 1 in JSON is bottom of room (y = 0)
                for (int y = 0; y < height; y++) {
                    int jsonRowIdx = height - 1 - y;
                    String rowString = (rows != null && jsonRowIdx >= 0 && jsonRowIdx < rows.size())
                        ? rows.get(jsonRowIdx) : "";
                    String[] rowTokens = rowString.trim().isEmpty() ? new String[0] : rowString.trim().split("\\s+");

                    for (int x = 0; x < width; x++) {
                        tokens[y * width + x] = (x < rowTokens.length) ? rowTokens[x] : RoomLayer.EMPTY_TOKEN;
                    }
                }

                layerMap.put(layerType, new RoomLayer(layerType, width, height, tokens));
            }
        }

        List<SpawnPoint> spawnPoints = new ArrayList<>();
        if (data.spawnPoints() != null) {
            for (SpawnPointData sp : data.spawnPoints()) {
                spawnPoints.add(new SpawnPoint(sp.id(), sp.x(), sp.y()));
            }
        }

        List<RoomEntityData> entities = new ArrayList<>();
        if (data.entities() != null) {
            for (EntityInstanceData ed : data.entities()) {
                com.makeasaying.engine.condition.CompiledCondition cond =
                    (ed.spawnCondition() != null && flagRegistry != null)
                        ? com.makeasaying.data.condition.ConditionLoader.parse(
                            ed.spawnCondition(), flagRegistry, atomRegistry != null ? atomRegistry : new com.makeasaying.engine.condition.AtomRegistry())
                        : com.makeasaying.engine.condition.CompiledCondition.ALWAYS_TRUE;
                String condJson = (ed.spawnCondition() != null && !ed.spawnCondition().isNull()) ? ed.spawnCondition().toString() : null;
                entities.add(new RoomEntityData(ed.instanceId(), ed.definition(), ed.x(), ed.y(), ed.overrides(), cond, condJson));
            }
        }

        List<com.makeasaying.engine.world.RoomExit> exits = new ArrayList<>();
        if (data.exits() != null) {
            for (RoomExitData ed : data.exits()) {
                com.makeasaying.engine.condition.CompiledCondition cond =
                    (ed.condition() != null && flagRegistry != null)
                        ? com.makeasaying.data.condition.ConditionLoader.parse(
                            ed.condition(), flagRegistry, atomRegistry != null ? atomRegistry : new com.makeasaying.engine.condition.AtomRegistry())
                        : com.makeasaying.engine.condition.CompiledCondition.ALWAYS_TRUE;
                String condJson = (ed.condition() != null && !ed.condition().isNull()) ? ed.condition().toString() : null;
                exits.add(new com.makeasaying.engine.world.RoomExit(
                    ed.id(), ed.x(), ed.y(), ed.width(), ed.height(), ed.targetRoom(), ed.targetSpawn(), cond, condJson
                ));
            }
        }

        return new Room(
            data.id(),
            width,
            height,
            data.entitySeq(),
            aliasMap,
            layerMap,
            spawnPoints,
            entities,
            exits
        );
    }

    public static RoomData toData(Room room, int version) {
        List<TilesetAliasData> tilesetEntries = new ArrayList<>();
        for (Map.Entry<String, String> entry : room.tilesetAliases().entrySet()) {
            tilesetEntries.add(new TilesetAliasData(entry.getKey(), entry.getValue()));
        }

        Map<String, List<String>> layersData = new LinkedHashMap<>();
        int width = room.width();
        int height = room.height();

        // Write layers in fixed order
        for (RoomLayerType layerType : RoomLayerType.values()) {
            RoomLayer layer = room.getLayer(layerType);
            if (layer == null) continue;

            List<String> rowStrings = new ArrayList<>();
            // Flip back from Y-up to top-to-bottom for serialization
            for (int jsonRow = 0; jsonRow < height; jsonRow++) {
                int y = height - 1 - jsonRow;
                StringBuilder sb = new StringBuilder();
                for (int x = 0; x < width; x++) {
                    if (x > 0) sb.append(' ');
                    sb.append(layer.getToken(x, y));
                }
                rowStrings.add(sb.toString());
            }
            layersData.put(layerType.id(), rowStrings);
        }

        List<SpawnPointData> spawnData = new ArrayList<>();
        for (SpawnPoint sp : room.spawnPoints()) {
            spawnData.add(new SpawnPointData(sp.id(), sp.x(), sp.y()));
        }

        List<EntityInstanceData> entityData = new ArrayList<>();
        for (RoomEntityData ed : room.entities()) {
            com.fasterxml.jackson.databind.JsonNode scNode = null;
            if (ed.spawnConditionJson() != null && !ed.spawnConditionJson().isBlank()) {
                try {
                    scNode = MAPPER.readTree(ed.spawnConditionJson());
                } catch (Exception ignored) {}
            }
            entityData.add(new EntityInstanceData(
                ed.instanceId(),
                ed.definition(),
                ed.x(),
                ed.y(),
                ed.overrides().isEmpty() ? null : ed.overrides(),
                scNode
            ));
        }

        List<RoomExitData> exitData = new ArrayList<>();
        for (com.makeasaying.engine.world.RoomExit exit : room.exits()) {
            com.fasterxml.jackson.databind.JsonNode cNode = null;
            if (exit.conditionJson() != null && !exit.conditionJson().isBlank()) {
                try {
                    cNode = MAPPER.readTree(exit.conditionJson());
                } catch (Exception ignored) {}
            }
            exitData.add(new RoomExitData(
                exit.id(),
                exit.x(),
                exit.y(),
                exit.width(),
                exit.height(),
                exit.targetRoom(),
                exit.targetSpawn(),
                cNode
            ));
        }

        return new RoomData(
            version,
            room.id(),
            width,
            height,
            room.entitySeq(),
            tilesetEntries,
            layersData,
            spawnData,
            entityData,
            exitData
        );
    }
}
