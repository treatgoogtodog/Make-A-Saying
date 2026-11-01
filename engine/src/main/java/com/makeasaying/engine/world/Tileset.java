package com.makeasaying.engine.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain runtime Tileset model per DEC-002 and M5a brief.
 * Holds tile definitions and sheet coordinates. Performs zero file I/O.
 */
public final class Tileset {
    private final String id;
    private final String area;
    private final int tileSize;
    private final String image;
    private final Map<String, TileDef> tiles;
    private final Map<String, TileSheetCoord> coords;

    public Tileset(String id, String area, int tileSize, String image, List<TileDef> tileList, Map<String, TileSheetCoord> coordMap) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Tileset id must not be blank");
        if (area == null || area.isBlank()) throw new IllegalArgumentException("Tileset area must not be blank");
        if (tileSize <= 0) throw new IllegalArgumentException("tileSize must be positive");
        if (image == null || image.isBlank()) throw new IllegalArgumentException("Tileset image must not be blank");

        this.id = id;
        this.area = area;
        this.tileSize = tileSize;
        this.image = image;

        Map<String, TileDef> tMap = new LinkedHashMap<>();
        if (tileList != null) {
            for (TileDef def : tileList) {
                tMap.put(def.id(), def);
            }
        }
        this.tiles = Collections.unmodifiableMap(tMap);
        this.coords = (coordMap != null) ? Collections.unmodifiableMap(new LinkedHashMap<>(coordMap)) : Collections.emptyMap();
    }

    public String id() {
        return id;
    }

    public String area() {
        return area;
    }

    public int tileSize() {
        return tileSize;
    }

    public String image() {
        return image;
    }

    public Map<String, TileDef> tiles() {
        return tiles;
    }

    public TileDef getTile(String tileId) {
        return tiles.get(tileId);
    }

    public TileSheetCoord getCoord(String tileId) {
        return coords.get(tileId);
    }

    public Map<String, TileSheetCoord> coords() {
        return coords;
    }
}
