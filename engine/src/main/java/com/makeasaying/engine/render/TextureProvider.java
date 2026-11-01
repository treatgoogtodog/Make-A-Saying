package com.makeasaying.engine.render;

import com.badlogic.gdx.graphics.Texture;

/**
 * Contract for texture retrieval with loud placeholder fallback.
 */
public interface TextureProvider {
    Texture get(String name);
    Texture getPlaceholder();
}
