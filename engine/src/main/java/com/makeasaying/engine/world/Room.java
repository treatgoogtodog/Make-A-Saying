package com.makeasaying.engine.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain runtime Room model per DEC-002 and M5a brief.
 * Stored Y-up. Performs zero file I/O.
 */
public final class Room {
    private final String id;
    private final int width;
    private final int height;
    private final Map<String, String> tilesetAliases;
    private final Map<RoomLayerType, RoomLayer> layers;
    private final List<SpawnPoint> spawnPoints;
    private final List<RoomEntityData> entities;
    private final List<RoomExit> exits;

    public Room(
        String id,
        int width,
        int height,
        Map<String, String> tilesetAliases,
        Map<RoomLayerType, RoomLayer> layers,
        List<SpawnPoint> spawnPoints,
        List<RoomEntityData> entities
    ) {
        this(id, width, height, tilesetAliases, layers, spawnPoints, entities, Collections.emptyList());
    }

    public Room(
        String id,
        int width,
        int height,
        Map<String, String> tilesetAliases,
        Map<RoomLayerType, RoomLayer> layers,
        List<SpawnPoint> spawnPoints,
        List<RoomEntityData> entities,
        List<RoomExit> exits
    ) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Room id must not be blank");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Room dimensions must be positive");

        this.id = id;
        this.width = width;
        this.height = height;
        this.tilesetAliases = (tilesetAliases != null) ? Collections.unmodifiableMap(new LinkedHashMap<>(tilesetAliases)) : Collections.emptyMap();
        this.layers = (layers != null) ? Collections.unmodifiableMap(new LinkedHashMap<>(layers)) : Collections.emptyMap();
        this.spawnPoints = (spawnPoints != null) ? List.copyOf(spawnPoints) : Collections.emptyList();
        this.entities = (entities != null) ? List.copyOf(entities) : Collections.emptyList();
        this.exits = (exits != null) ? List.copyOf(exits) : Collections.emptyList();
    }

    public String id() {
        return id;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Map<String, String> tilesetAliases() {
        return tilesetAliases;
    }

    public String getTilesetIdForAlias(String alias) {
        return tilesetAliases.get(alias);
    }

    public Map<RoomLayerType, RoomLayer> layers() {
        return layers;
    }

    public RoomLayer getLayer(RoomLayerType type) {
        return layers.get(type);
    }

    public RoomLayer getMainLayer() {
        return layers.get(RoomLayerType.MAIN);
    }

    public List<SpawnPoint> spawnPoints() {
        return spawnPoints;
    }

    public SpawnPoint getSpawnPoint(String spawnId) {
        for (SpawnPoint sp : spawnPoints) {
            if (sp.id().equals(spawnId)) {
                return sp;
            }
        }
        return null;
    }

    public List<RoomEntityData> entities() {
        return entities;
    }

    public List<RoomExit> exits() {
        return exits;
    }
}
