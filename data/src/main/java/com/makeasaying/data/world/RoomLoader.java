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
    public static final int CURRENT_VERSION = 2;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final MigrationChain MIGRATION_CHAIN = new MigrationChain("room", CURRENT_VERSION);

    static {
        // v1 -> v2: Add empty "exits" array if not present
        MIGRATION_CHAIN.register(1, root -> {
            if (!root.has("exits")) {
                root.putArray("exits");
            }
        });
    }

    private RoomLoader() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"version", "id", "width", "height", "tilesets", "layers", "spawnPoints", "entities", "exits"})
    public record RoomData(
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
            this(version, id, width, height, tilesets, layers, spawnPoints, entities, Collections.emptyList());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"alias", "id"})
    public record TilesetAliasData(String alias, String id) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"id", "x", "y"})
    public record SpawnPointData(String id, int x, int y) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"id", "x", "y", "width", "height", "targetRoom", "targetSpawn"})
    public record RoomExitData(
        String id,
        int x,
        int y,
        @com.fasterxml.jackson.annotation.JsonProperty("width") @com.fasterxml.jackson.annotation.JsonAlias({"w"}) int width,
        @com.fasterxml.jackson.annotation.JsonProperty("height") @com.fasterxml.jackson.annotation.JsonAlias({"h"}) int height,
        String targetRoom,
        String targetSpawn
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"instanceId", "definition", "x", "y", "overrides"})
    public record EntityInstanceData(
        String instanceId,
        String definition,
        int x,
        int y,
        Map<String, Object> overrides
    ) {}

    public static Room load(Path path) throws IOException {
        if (!Files.exists(path)) {
            Log.error(LogCategory.DATA, "Room file not found: %s", path.toAbsolutePath());
            throw new IllegalArgumentException("Room file not found: " + path);
        }

        RoomData data;
        try {
            com.fasterxml.jackson.databind.JsonNode rootNode = MAPPER.readTree(path.toFile());
            if (rootNode instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
                MIGRATION_CHAIN.migrate(objectNode);
                data = MAPPER.treeToValue(objectNode, RoomData.class);
            } else {
                throw new IOException("Root of room file must be a JSON object: " + path);
            }
        } catch (Exception e) {
            Log.error(LogCategory.DATA, "Failed to parse room file: %s", e, path);
            if (e instanceof IOException ioException) throw ioException;
            if (e instanceof IllegalArgumentException iae) throw iae;
            throw new IOException("Malformed room file: " + path, e);
        }

        return fromData(data);
    }

    public static Room fromData(RoomData data) {
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
                entities.add(new RoomEntityData(ed.instanceId(), ed.definition(), ed.x(), ed.y(), ed.overrides()));
            }
        }

        List<com.makeasaying.engine.world.RoomExit> exits = new ArrayList<>();
        if (data.exits() != null) {
            for (RoomExitData ed : data.exits()) {
                exits.add(new com.makeasaying.engine.world.RoomExit(
                    ed.id(), ed.x(), ed.y(), ed.width(), ed.height(), ed.targetRoom(), ed.targetSpawn()
                ));
            }
        }

        return new Room(
            data.id(),
            width,
            height,
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
            entityData.add(new EntityInstanceData(
                ed.instanceId(),
                ed.definition(),
                ed.x(),
                ed.y(),
                ed.overrides().isEmpty() ? null : ed.overrides()
            ));
        }

        List<RoomExitData> exitData = new ArrayList<>();
        for (com.makeasaying.engine.world.RoomExit exit : room.exits()) {
            exitData.add(new RoomExitData(
                exit.id(),
                exit.x(),
                exit.y(),
                exit.width(),
                exit.height(),
                exit.targetRoom(),
                exit.targetSpawn()
            ));
        }

        return new RoomData(
            version,
            room.id(),
            width,
            height,
            tilesetEntries,
            layersData,
            spawnData,
            entityData,
            exitData
        );
    }
}
