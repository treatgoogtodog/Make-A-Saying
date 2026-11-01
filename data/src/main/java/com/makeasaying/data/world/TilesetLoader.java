package com.makeasaying.data.world;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeasaying.engine.entity.TileShape;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;
import com.makeasaying.engine.world.TileDef;
import com.makeasaying.engine.world.TileSheetCoord;
import com.makeasaying.engine.world.Tileset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads Tileset files from content/tilesets/<id>.json per M5a brief.
 */
public final class TilesetLoader {
    public static final int CURRENT_VERSION = 1;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TilesetLoader() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"version", "id", "area", "tileSize", "image", "tiles"})
    public record TilesetData(
        int version,
        String id,
        String area,
        int tileSize,
        String image,
        List<TileEntryData> tiles
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"id", "col", "row", "shape", "role", "tags"})
    public record TileEntryData(
        String id,
        int col,
        int row,
        String shape,
        String role,
        List<String> tags
    ) {}

    public static Tileset load(Path path) throws IOException {
        if (!Files.exists(path)) {
            Log.error(LogCategory.DATA, "Tileset file not found: %s", path.toAbsolutePath());
            throw new IllegalArgumentException("Tileset file not found: " + path);
        }

        TilesetData data;
        try {
            data = MAPPER.readValue(path.toFile(), TilesetData.class);
        } catch (Exception e) {
            Log.error(LogCategory.DATA, "Failed to parse tileset file: %s", e, path);
            throw new IOException("Malformed tileset file: " + path, e);
        }

        if (data.version() > CURRENT_VERSION) {
            Log.error(LogCategory.DATA, "Unknown newer tileset version %d in %s (max supported is %d)",
                data.version(), path, CURRENT_VERSION);
            throw new IllegalArgumentException("Unknown newer tileset version: " + data.version());
        }

        return fromData(data);
    }

    public static Tileset fromData(TilesetData data) {
        if (data.version() > CURRENT_VERSION) {
            throw new IllegalArgumentException("Unknown newer tileset version: " + data.version());
        }

        List<TileDef> tileList = new ArrayList<>();
        Map<String, TileSheetCoord> coords = new LinkedHashMap<>();

        if (data.tiles() != null) {
            for (TileEntryData t : data.tiles()) {
                TileShape shape = TileShape.fromName(t.shape());
                TileDef def = new TileDef(t.id(), shape, t.role(), t.tags());
                tileList.add(def);
                coords.put(t.id(), new TileSheetCoord(t.col(), t.row()));
            }
        }

        return new Tileset(
            data.id(),
            data.area(),
            data.tileSize(),
            data.image(),
            tileList,
            coords
        );
    }

    public static TilesetData toData(Tileset tileset, int version) {
        List<TileEntryData> tileEntries = new ArrayList<>();
        for (TileDef def : tileset.tiles().values()) {
            TileSheetCoord coord = tileset.getCoord(def.id());
            int col = coord != null ? coord.col() : 0;
            int row = coord != null ? coord.row() : 0;
            tileEntries.add(new TileEntryData(
                def.id(),
                col,
                row,
                def.shape().name(),
                def.role(),
                def.tags().isEmpty() ? null : def.tags()
            ));
        }

        return new TilesetData(
            version,
            tileset.id(),
            tileset.area(),
            tileset.tileSize(),
            tileset.image(),
            tileEntries
        );
    }
}
