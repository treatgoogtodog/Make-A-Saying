package com.makeasaying.engine.render;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomLayer;
import com.makeasaying.engine.world.RoomLayerType;
import com.makeasaying.engine.world.TileSheetCoord;
import com.makeasaying.engine.world.Tileset;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders Room layers to a SpriteBatch with camera offset and viewport culling per M5a brief.
 * Resolves alias:tileId to TextureRegion.
 * Fallback uses magenta placeholder tile for unknown or missing assets.
 */
public final class RoomRenderer {
    private final Map<String, TextureRegion> regionCache = new HashMap<>();
    private final TextureRegion placeholderRegion;

    public RoomRenderer(Texture fallbackTexture) {
        if (fallbackTexture != null) {
            this.placeholderRegion = new TextureRegion(fallbackTexture);
        } else {
            this.placeholderRegion = null;
        }
    }

    public void registerTileset(Tileset tileset, Texture texture) {
        if (tileset == null || texture == null) return;
        int tileSize = tileset.tileSize();

        for (Map.Entry<String, TileSheetCoord> entry : tileset.coords().entrySet()) {
            String tileId = entry.getKey();
            TileSheetCoord coord = entry.getValue();
            String key = tileset.id() + ":" + tileId;

            TextureRegion region = new TextureRegion(
                texture,
                coord.col() * tileSize,
                coord.row() * tileSize,
                tileSize,
                tileSize
            );
            regionCache.put(key, region);
        }
    }

    public void renderLayer(SpriteBatch batch, Room room, RoomLayerType layerType,
                            Map<String, Tileset> tilesets, int camX, int camY, int viewW, int viewH, int tileSize) {
        if (room == null || layerType == null) return;
        RoomLayer layer = room.getLayer(layerType);
        if (layer == null) return;

        int minTileX = Math.max(0, camX / tileSize);
        int maxTileX = Math.min(room.width() - 1, (camX + viewW) / tileSize);
        int minTileY = Math.max(0, camY / tileSize);
        int maxTileY = Math.min(room.height() - 1, (camY + viewH) / tileSize);

        for (int y = minTileY; y <= maxTileY; y++) {
            for (int x = minTileX; x <= maxTileX; x++) {
                String token = layer.getToken(x, y);
                if (token == null || token.equals(RoomLayer.EMPTY_TOKEN)) {
                    continue;
                }

                TextureRegion region = resolveRegion(room, tilesets, token);
                if (region != null) {
                    int drawX = x * tileSize - camX;
                    int drawY = y * tileSize - camY;
                    batch.draw(region, drawX, drawY, tileSize, tileSize);
                }
            }
        }
    }

    private TextureRegion resolveRegion(Room room, Map<String, Tileset> tilesets, String token) {
        String[] parts = token.split(":", 2);
        if (parts.length != 2) {
            return placeholderRegion;
        }

        String alias = parts[0];
        String tileId = parts[1];
        String tilesetId = room.getTilesetIdForAlias(alias);
        if (tilesetId == null) {
            return placeholderRegion;
        }

        String key = tilesetId + ":" + tileId;
        TextureRegion region = regionCache.get(key);
        if (region != null) {
            return region;
        }

        // Try placeholder if unknown tile in known tileset
        return placeholderRegion;
    }

    public TextureRegion getRegion(String tilesetId, String tileId) {
        String key = tilesetId + ":" + tileId;
        TextureRegion region = regionCache.get(key);
        return region != null ? region : placeholderRegion;
    }

    public TextureRegion getPlaceholderRegion() {
        return placeholderRegion;
    }

    public void clearCache() {
        regionCache.clear();
    }
}
