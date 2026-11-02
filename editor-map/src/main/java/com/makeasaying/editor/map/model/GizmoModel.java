package com.makeasaying.editor.map.model;

import com.makeasaying.engine.entity.EntityDefinition;
import com.makeasaying.engine.world.RoomEntityData;

import java.util.Map;

/**
 * Pure math and hit-testing model for editor viewport entity gizmos.
 * Provides bounding boxes, platform trajectory endpoints, and handle hit tests.
 */
public final class GizmoModel {
    public static final int DEFAULT_HANDLE_RADIUS = 6;

    public record PlatformGizmo(
        int startX,
        int startY,
        int endX,
        int endY,
        int travelX,
        int travelY
    ) {}

    public static PlatformGizmo computePlatformGizmo(RoomEntityData entity, EntityDefinition def) {
        if (entity == null) return null;

        boolean hasPlatform = false;
        int tx = 0;
        int ty = 0;

        if (def != null && def.components() != null && def.components().containsKey("platform")) {
            hasPlatform = true;
            Map<String, Object> plat = def.components().get("platform");
            tx = ((Number) plat.getOrDefault("travelX", 0)).intValue();
            ty = ((Number) plat.getOrDefault("travelY", 0)).intValue();
        }

        if (entity.overrides() != null && entity.overrides().containsKey("platform")) {
            hasPlatform = true;
            Map<String, Object> ovrPlat = entity.overrides().get("platform");
            if (ovrPlat.containsKey("travelX")) tx = ((Number) ovrPlat.get("travelX")).intValue();
            if (ovrPlat.containsKey("travelY")) ty = ((Number) ovrPlat.get("travelY")).intValue();
        }

        if (!hasPlatform) return null;

        int sx = entity.x();
        int sy = entity.y();
        return new PlatformGizmo(sx, sy, sx + tx, sy + ty, tx, ty);
    }

    public static boolean hitTestHandle(int clickX, int clickY, int handleX, int handleY, int radius) {
        int dx = clickX - handleX;
        int dy = clickY - handleY;
        return (dx * dx + dy * dy) <= (radius * radius);
    }
}
