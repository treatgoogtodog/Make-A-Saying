package com.makeasaying.editor.map.model;

import com.makeasaying.engine.entity.EntityDefinition;
import com.makeasaying.engine.world.RoomEntityData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Pure selection model for editor entity picking and selection state.
 * Implements deterministic tie-breaking:
 * 1. Smallest collider area first.
 * 2. Lowest numeric sequence number.
 * Entities without a collider use a 16x16 fallback footprint.
 */
public final class SelectionModel {
    private String selectedInstanceId;

    public SelectionModel() {}

    public String getSelectedInstanceId() {
        return selectedInstanceId;
    }

    public void setSelectedInstanceId(String selectedInstanceId) {
        this.selectedInstanceId = selectedInstanceId;
    }

    public void clearSelection() {
        this.selectedInstanceId = null;
    }

    public boolean isSelected(String instanceId) {
        return instanceId != null && instanceId.equals(selectedInstanceId);
    }

    public record EntityHitBox(
        RoomEntityData entity,
        int left,
        int bottom,
        int width,
        int height,
        int area,
        int seq
    ) {}

    public static EntityHitBox computeHitBox(RoomEntityData entity, EntityDefinition def) {
        int w = 16;
        int h = 16;
        int ox = -8;
        int oy = 0;

        if (def != null && def.components() != null) {
            Map<String, Object> col = def.components().get("collider");
            if (col != null) {
                w = ((Number) col.getOrDefault("width", 16)).intValue();
                h = ((Number) col.getOrDefault("height", 16)).intValue();
                ox = ((Number) col.getOrDefault("offsetX", -w / 2)).intValue();
                oy = ((Number) col.getOrDefault("offsetY", 0)).intValue();
            }
        }

        if (entity.overrides() != null && entity.overrides().containsKey("collider")) {
            Map<String, Object> ovrCol = entity.overrides().get("collider");
            if (ovrCol.containsKey("width")) w = ((Number) ovrCol.get("width")).intValue();
            if (ovrCol.containsKey("height")) h = ((Number) ovrCol.get("height")).intValue();
            if (ovrCol.containsKey("offsetX")) ox = ((Number) ovrCol.get("offsetX")).intValue();
            if (ovrCol.containsKey("offsetY")) oy = ((Number) ovrCol.get("offsetY")).intValue();
        }

        int left = entity.x() + ox;
        int bottom = entity.y() + oy;
        int area = w * h;

        int seq = Integer.MAX_VALUE;
        String iid = entity.instanceId();
        int idx = iid.lastIndexOf('_');
        if (idx >= 0 && idx < iid.length() - 1) {
            try {
                seq = Integer.parseInt(iid.substring(idx + 1));
            } catch (NumberFormatException ignored) {}
        }

        return new EntityHitBox(entity, left, bottom, w, h, area, seq);
    }

    /**
     * Finds the entity intersecting (clickX, clickY) with deterministic tie-breaking.
     */
    public RoomEntityData pickEntity(
        int clickX,
        int clickY,
        List<RoomEntityData> entities,
        Map<String, EntityDefinition> defs
    ) {
        if (entities == null || entities.isEmpty()) return null;

        List<EntityHitBox> candidates = new ArrayList<>();
        for (RoomEntityData ed : entities) {
            EntityDefinition def = (defs != null) ? defs.get(ed.definition()) : null;
            EntityHitBox hitBox = computeHitBox(ed, def);
            if (clickX >= hitBox.left() && clickX < hitBox.left() + hitBox.width() &&
                clickY >= hitBox.bottom() && clickY < hitBox.bottom() + hitBox.height()) {
                candidates.add(hitBox);
            }
        }

        if (candidates.isEmpty()) return null;

        // Sort by area ascending, then seq ascending
        candidates.sort(Comparator.comparingInt(EntityHitBox::area).thenComparingInt(EntityHitBox::seq));
        return candidates.get(0).entity();
    }
}
