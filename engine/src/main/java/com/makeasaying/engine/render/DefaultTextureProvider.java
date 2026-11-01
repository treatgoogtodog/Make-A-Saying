package com.makeasaying.engine.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

import java.util.HashMap;
import java.util.Map;

/**
 * Texture provider with 16x16 magenta/black checker fallback per AGENTS.md rule 7 and M1 brief.
 */
public class DefaultTextureProvider implements TextureProvider, Disposable {
    private final Map<String, Texture> cache = new HashMap<>();
    private Texture placeholderTexture;

    public DefaultTextureProvider() {}

    public Texture createPlaceholderTexture() {
        if (placeholderTexture != null) {
            return placeholderTexture;
        }

        Pixmap pixmap = new Pixmap(CheckerGenerator.SIZE, CheckerGenerator.SIZE, Pixmap.Format.RGBA8888);
        int[] pixels = CheckerGenerator.generatePixels();
        for (int y = 0; y < CheckerGenerator.SIZE; y++) {
            for (int x = 0; x < CheckerGenerator.SIZE; x++) {
                pixmap.drawPixel(x, y, pixels[y * CheckerGenerator.SIZE + x]);
            }
        }

        placeholderTexture = new Texture(pixmap);
        placeholderTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        placeholderTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        pixmap.dispose();

        return placeholderTexture;
    }

    public void register(String name, Texture texture) {
        if (name != null && texture != null) {
            cache.put(name, texture);
        }
    }

    @Override
    public Texture getPlaceholder() {
        if (placeholderTexture == null) {
            createPlaceholderTexture();
        }
        return placeholderTexture;
    }

    @Override
    public Texture get(String name) {
        if (name != null && cache.containsKey(name)) {
            return cache.get(name);
        }

        Log.error(LogCategory.ASSETS, "Asset not found: '%s'. Using magenta/black placeholder texture.", name);
        return getPlaceholder();
    }

    @Override
    public void dispose() {
        for (Texture texture : cache.values()) {
            texture.dispose();
        }
        cache.clear();

        if (placeholderTexture != null) {
            placeholderTexture.dispose();
            placeholderTexture = null;
        }
    }
}
