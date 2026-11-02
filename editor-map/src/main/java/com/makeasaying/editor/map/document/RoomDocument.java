package com.makeasaying.editor.map.document;

import com.makeasaying.editor.core.command.UndoStack;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomEntityData;
import com.makeasaying.engine.world.RoomExit;
import com.makeasaying.engine.world.RoomLayer;
import com.makeasaying.engine.world.RoomLayerType;
import com.makeasaying.engine.world.SpawnPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable in-memory room document model for editors per M6a brief.
 * Pure logic, headless testable.
 * Guarantees byte-identical round trip with RoomLoader and RoomWriter.
 * Enforces layer locking, undo/redo through UndoStack, and single-pipeline data translation.
 */
public final class RoomDocument {
    private String id;
    private int width;
    private int height;
    private int entitySeq;
    private int savedFloor;

    private final Map<String, String> tilesetAliases = new LinkedHashMap<>();
    private final Map<RoomLayerType, String[]> layers = new EnumMap<>(RoomLayerType.class);
    private final Map<RoomLayerType, Boolean> layerLocked = new EnumMap<>(RoomLayerType.class);
    private final Map<RoomLayerType, Boolean> layerVisible = new EnumMap<>(RoomLayerType.class);

    private final List<SpawnPoint> spawnPoints = new ArrayList<>();
    private final List<RoomExit> exits = new ArrayList<>();
    private final List<RoomEntityData> entities = new ArrayList<>();

    private final UndoStack undoStack;

    public RoomDocument(String id, int width, int height, UndoStack undoStack) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Room id must not be blank");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Dimensions must be positive");

        this.id = id;
        this.width = width;
        this.height = height;
        this.undoStack = undoStack != null ? undoStack : new UndoStack();

        // Initialize default layers
        for (RoomLayerType type : RoomLayerType.values()) {
            String[] tokens = new String[width * height];
            for (int i = 0; i < tokens.length; i++) {
                tokens[i] = RoomLayer.EMPTY_TOKEN;
            }
            layers.put(type, tokens);
            layerLocked.put(type, false);
            layerVisible.put(type, true);
        }
    }

    public RoomDocument(String id, int width, int height) {
        this(id, width, height, new UndoStack());
    }

    public static RoomDocument fromRoom(Room room, UndoStack undoStack) {
        if (room == null) throw new IllegalArgumentException("Room must not be null");

        RoomDocument doc = new RoomDocument(room.id(), room.width(), room.height(), undoStack);
        doc.tilesetAliases.clear();
        doc.tilesetAliases.putAll(room.tilesetAliases());

        for (RoomLayerType type : RoomLayerType.values()) {
            RoomLayer layer = room.getLayer(type);
            String[] tokens = new String[room.width() * room.height()];
            if (layer != null) {
                for (int y = 0; y < room.height(); y++) {
                    for (int x = 0; x < room.width(); x++) {
                        tokens[y * room.width() + x] = layer.getToken(x, y);
                    }
                }
            } else {
                for (int i = 0; i < tokens.length; i++) {
                    tokens[i] = RoomLayer.EMPTY_TOKEN;
                }
            }
            doc.layers.put(type, tokens);
            doc.layerLocked.put(type, false);
            doc.layerVisible.put(type, true);
        }

        doc.spawnPoints.clear();
        doc.spawnPoints.addAll(room.spawnPoints());

        doc.exits.clear();
        doc.exits.addAll(room.exits());

        doc.entities.clear();
        doc.entities.addAll(room.entities());
        doc.entitySeq = Math.max(1, room.entitySeq());
        doc.savedFloor = Math.max(0, room.entitySeq() - 1);

        doc.undoStack.markSaved();
        return doc;
    }

    public static RoomDocument fromRoom(Room room) {
        return fromRoom(room, new UndoStack());
    }

    public Room toRoom() {
        Map<RoomLayerType, RoomLayer> layerMap = new LinkedHashMap<>();
        for (Map.Entry<RoomLayerType, String[]> entry : layers.entrySet()) {
            RoomLayerType type = entry.getKey();
            String[] tokens = entry.getValue();
            // Clone tokens array to guarantee immutability
            String[] cloned = tokens.clone();
            layerMap.put(type, new RoomLayer(type, width, height, cloned));
        }

        return new Room(
            id,
            width,
            height,
            entitySeq,
            new LinkedHashMap<>(tilesetAliases),
            layerMap,
            new ArrayList<>(spawnPoints),
            new ArrayList<>(entities),
            new ArrayList<>(exits)
        );
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public Map<String, String> getTilesetAliases() { return tilesetAliases; }
    public List<SpawnPoint> getSpawnPoints() { return spawnPoints; }
    public List<RoomExit> getExits() { return exits; }
    public List<RoomEntityData> getEntities() { return entities; }
    public UndoStack getUndoStack() { return undoStack; }

    public int getEntitySeq() { return entitySeq; }
    public void setEntitySeq(int entitySeq) { this.entitySeq = entitySeq; }

    public int getSavedFloor() { return savedFloor; }
    public void setSavedFloor(int savedFloor) { this.savedFloor = savedFloor; }

    public void updateSavedFloorOnSave() {
        this.savedFloor = Math.max(this.savedFloor, this.entitySeq - 1);
        undoStack.markSaved();
    }

    public int allocateEntitySeq() {
        return entitySeq++;
    }

    public void restoreEntitySeq(int seq) {
        // DEC-028: Suffixes that have reached disk are never reused
        this.entitySeq = Math.max(this.savedFloor + 1, seq);
    }

    public void addEntity(RoomEntityData entity) {
        if (entity == null) return;
        entities.add(entity);
    }

    public RoomEntityData removeEntity(String instanceId) {
        for (int i = 0; i < entities.size(); i++) {
            if (entities.get(i).instanceId().equals(instanceId)) {
                return entities.remove(i);
            }
        }
        return null;
    }

    public RoomEntityData getEntity(String instanceId) {
        for (RoomEntityData ed : entities) {
            if (ed.instanceId().equals(instanceId)) {
                return ed;
            }
        }
        return null;
    }

    public void setEntity(String instanceId, RoomEntityData updated) {
        for (int i = 0; i < entities.size(); i++) {
            if (entities.get(i).instanceId().equals(instanceId)) {
                entities.set(i, updated);
                return;
            }
        }
        entities.add(updated);
    }

    public boolean isLayerLocked(RoomLayerType type) {
        return layerLocked.getOrDefault(type, false);
    }

    public void setLayerLocked(RoomLayerType type, boolean locked) {
        layerLocked.put(type, locked);
    }

    public boolean isLayerVisible(RoomLayerType type) {
        return layerVisible.getOrDefault(type, true);
    }

    public void setLayerVisible(RoomLayerType type, boolean visible) {
        layerVisible.put(type, visible);
    }

    public String getToken(RoomLayerType type, int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return RoomLayer.EMPTY_TOKEN;
        }
        String[] tokens = layers.get(type);
        if (tokens == null) return RoomLayer.EMPTY_TOKEN;
        return tokens[y * width + x];
    }

    /**
     * Direct token setter called by EditorCommand implementations.
     */
    public void setTokenInternal(RoomLayerType type, int x, int y, String token) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        String[] tokens = layers.get(type);
        if (tokens != null) {
            tokens[y * width + x] = token != null ? token : RoomLayer.EMPTY_TOKEN;
        }
    }

    /**
     * Direct dimensions and layer arrays setter called by ResizeRoomCommand.
     */
    public void resizeInternal(int newWidth, int newHeight, Map<RoomLayerType, String[]> newLayers) {
        this.width = newWidth;
        this.height = newHeight;
        this.layers.clear();
        this.layers.putAll(newLayers);
    }

    public int countTokenUsages(String tokenPrefix) {
        int count = 0;
        for (String[] layerTokens : layers.values()) {
            if (layerTokens == null) continue;
            for (String t : layerTokens) {
                if (t != null && t.startsWith(tokenPrefix)) {
                    count++;
                }
            }
        }
        return count;
    }
}
