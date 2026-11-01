package com.makeasaying.engine.entity;

/**
 * Global tile shapes per AGENTS.md section 7.5.
 */
public enum TileShape {
    EMPTY((byte) 0),
    SOLID((byte) 1),
    ONE_WAY((byte) 2),
    SLOPE_45_UP_RIGHT((byte) 3),
    SLOPE_45_UP_LEFT((byte) 4);

    public final byte id;

    TileShape(byte id) {
        this.id = id;
    }

    public static TileShape fromId(byte id) {
        return switch (id) {
            case 0 -> EMPTY;
            case 1 -> SOLID;
            case 2 -> ONE_WAY;
            case 3 -> SLOPE_45_UP_RIGHT;
            case 4 -> SLOPE_45_UP_LEFT;
            default -> throw new IllegalArgumentException("Unknown tile shape id: " + id);
        };
    }

    public static TileShape fromName(String name) {
        if (name == null) {
            return EMPTY;
        }
        return TileShape.valueOf(name.toUpperCase(java.util.Locale.ROOT));
    }
}
