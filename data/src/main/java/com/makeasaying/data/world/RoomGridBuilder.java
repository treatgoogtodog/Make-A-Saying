package com.makeasaying.data.world;

import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.engine.entity.TileShape;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomLayer;
import com.makeasaying.engine.world.TileDef;
import com.makeasaying.engine.world.Tileset;

import java.util.Map;

/**
 * Builds runtime TileGrid from Room's main layer per M5a brief.
 * Missing tileset or tile is logged loudly as an error and falls back to TileShape.SOLID in dev builds.
 */
public final class RoomGridBuilder {
    private RoomGridBuilder() {}

    public static TileGrid build(Room room, Map<String, Tileset> loadedTilesets, int tileSize) {
        if (room == null) throw new IllegalArgumentException("Room must not be null");

        TileGrid grid = new TileGrid(room.width(), room.height(), tileSize);
        RoomLayer mainLayer = room.getMainLayer();
        if (mainLayer == null) {
            Log.error(LogCategory.DATA, "Room '%s' has no main layer! Collision grid will be empty.", room.id());
            return grid;
        }

        for (int y = 0; y < room.height(); y++) {
            for (int x = 0; x < room.width(); x++) {
                String token = mainLayer.getToken(x, y);
                if (token == null || token.equals(RoomLayer.EMPTY_TOKEN)) {
                    grid.setTile(x, y, TileShape.EMPTY);
                    continue;
                }

                String[] parts = token.split(":", 2);
                if (parts.length != 2) {
                    Log.error(LogCategory.DATA, "Room '%s' (%d, %d): invalid token '%s'. Fallback to SOLID.",
                        room.id(), x, y, token);
                    grid.setTile(x, y, TileShape.SOLID);
                    continue;
                }

                String alias = parts[0];
                String tileId = parts[1];

                String tilesetId = room.getTilesetIdForAlias(alias);
                if (tilesetId == null) {
                    Log.error(LogCategory.DATA, "Room '%s' (%d, %d): unknown tileset alias '%s' for token '%s'. Fallback to SOLID.",
                        room.id(), x, y, alias, token);
                    grid.setTile(x, y, TileShape.SOLID);
                    continue;
                }

                Tileset tileset = (loadedTilesets != null) ? loadedTilesets.get(tilesetId) : null;
                if (tileset == null) {
                    Log.error(LogCategory.DATA, "Room '%s' (%d, %d): tileset '%s' not loaded for token '%s'. Fallback to SOLID.",
                        room.id(), x, y, tilesetId, token);
                    grid.setTile(x, y, TileShape.SOLID);
                    continue;
                }

                TileDef tileDef = tileset.getTile(tileId);
                if (tileDef == null) {
                    Log.error(LogCategory.DATA, "Room '%s' (%d, %d): unknown tile '%s' in tileset '%s'. Fallback to SOLID.",
                        room.id(), x, y, tileId, tilesetId);
                    grid.setTile(x, y, TileShape.SOLID);
                    continue;
                }

                grid.setTile(x, y, tileDef.shape());
            }
        }

        return grid;
    }
}
