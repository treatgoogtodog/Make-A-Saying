package com.makeasaying.editor.map.dialogs;

import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.engine.world.SpawnPoint;

import java.util.regex.Pattern;

/**
 * Pure model for New Room dialog per M6a brief.
 */
public final class NewRoomModel {
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_.]+$");

    private String id = "new_room";
    private int width = 20;
    private int height = 12;
    private String defaultAlias = "a";
    private String defaultTilesetId = "test_area";

    public String getId() { return id; }
    public void setId(String id) { this.id = id != null ? id.trim() : ""; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public String getDefaultAlias() { return defaultAlias; }
    public void setDefaultAlias(String defaultAlias) { this.defaultAlias = defaultAlias; }

    public String getDefaultTilesetId() { return defaultTilesetId; }
    public void setDefaultTilesetId(String defaultTilesetId) { this.defaultTilesetId = defaultTilesetId; }

    public boolean isValid() {
        return !id.isBlank() && ID_PATTERN.matcher(id).matches() && width > 0 && height > 0;
    }

    public RoomDocument createDocument() {
        if (!isValid()) {
            throw new IllegalStateException("Cannot create document with invalid settings: id=" + id + ", w=" + width + ", h=" + height);
        }
        RoomDocument doc = new RoomDocument(id, width, height);
        if (defaultAlias != null && !defaultAlias.isBlank() && defaultTilesetId != null && !defaultTilesetId.isBlank()) {
            doc.getTilesetAliases().put(defaultAlias, defaultTilesetId);
        }
        doc.getSpawnPoints().add(new SpawnPoint("default", 32, 16));
        return doc;
    }
}
