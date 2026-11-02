package com.makeasaying.engine.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.PlatformComponent;

/**
 * Temporary entity renderer for M6b.
 * // TEMPORARY(M9): This class will be replaced in M9 when the full sprite and animation runtime is introduced.
 * Renders filled bounding box with debugColor at collider position for all alive entities.
 * Single draw path for entities in both Game and Editor modes.
 */
public final class EntityDebugRenderer {
    private final Texture pixelTexture;
    private final boolean ownsTexture;

    public EntityDebugRenderer() {
        Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pix.setColor(Color.WHITE);
        pix.fill();
        this.pixelTexture = new Texture(pix);
        pix.dispose();
        this.ownsTexture = true;
    }

    public EntityDebugRenderer(Texture pixelTexture) {
        this.pixelTexture = pixelTexture;
        this.ownsTexture = false;
    }

    public void render(SpriteBatch batch, World world, float alpha, int camX, int camY) {
        if (world == null || batch == null) return;

        for (int i = 1; i < World.MAX_ENTITIES; i++) {
            if (!world.isAliveIndex(i)) continue;

            PlatformComponent plat = world.getPlatformByIndex(i);
            if (plat != null) {
                int pX = Snap.lerpAndSnap(plat.prevX, plat.currentX, alpha) - camX;
                int pY = Snap.lerpAndSnap(plat.prevY, plat.currentY, alpha) - camY;
                batch.setColor(0.27f, 0.53f, 1.0f, 0.9f); // #4488FF platform blue
                batch.draw(pixelTexture, pX, pY, plat.width, plat.height);
                batch.setColor(Color.WHITE);
                continue;
            }

            BodyComponent body = world.getBodyByIndex(i);
            ColliderComponent col = world.getColliderByIndex(i);
            if (body != null && col != null) {
                int renderX = Snap.lerpAndSnap(body.prevX, body.x, alpha) - camX;
                int renderY = Snap.lerpAndSnap(body.prevY, body.y, alpha) - camY;
                int drawX = renderX + col.offsetX;
                int drawY = renderY + col.offsetY;

                batch.setColor(0.0f, 0.9f, 1.0f, 0.8f); // Cyan-ish player/entity color
                batch.draw(pixelTexture, drawX, drawY, col.width, col.height);
                batch.setColor(Color.WHITE);
            }
        }
    }

    public void dispose() {
        if (ownsTexture && pixelTexture != null) {
            pixelTexture.dispose();
        }
    }
}
