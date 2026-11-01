package com.makeasaying.engine.world;

import java.util.Locale;

/**
 * Standard room layers per AGENTS.md section 7.13 and M5a brief.
 * Drawn in fixed order: far_background -> near_background -> main -> decoration -> (entities) -> foreground.
 */
public enum RoomLayerType {
    FAR_BACKGROUND("far_background", false),
    NEAR_BACKGROUND("near_background", false),
    MAIN("main", true),
    DECORATION("decoration", false),
    FOREGROUND("foreground", false);

    private final String id;
    private final boolean required;

    RoomLayerType(String id, boolean required) {
        this.id = id;
        this.required = required;
    }

    public String id() {
        return id;
    }

    public boolean isRequired() {
        return required;
    }

    public static RoomLayerType fromId(String id) {
        if (id == null) return null;
        for (RoomLayerType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
