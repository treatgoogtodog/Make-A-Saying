package com.makeasaying.engine.world;

import com.makeasaying.engine.entity.TileShape;

import java.util.Collections;
import java.util.List;

/**
 * Definition of a single tile within a tileset per AGENTS.md section 7.13 and M5a brief.
 */
public record TileDef(
    String id,
    TileShape shape,
    String role,
    List<String> tags
) {
    public TileDef {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Tile ID must not be blank");
        }
        if (shape == null) {
            shape = TileShape.SOLID;
        }
        tags = (tags != null) ? List.copyOf(tags) : Collections.emptyList();
    }

    public TileDef(String id, TileShape shape) {
        this(id, shape, null, Collections.emptyList());
    }
}
