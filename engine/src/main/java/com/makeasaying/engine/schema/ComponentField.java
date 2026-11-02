package com.makeasaying.engine.schema;

import java.util.Collections;
import java.util.List;

/**
 * Definition of a single property field within a component schema.
 */
public record ComponentField(
    String name,
    FieldType type,
    Object defaultValue,
    Number min,
    Number max,
    String tooltip,
    boolean overridable,
    List<String> enumConstants
) {
    public ComponentField {
        enumConstants = (enumConstants != null) ? Collections.unmodifiableList(enumConstants) : Collections.emptyList();
    }

    public static ComponentField integerField(String name, int defaultValue, int min, int max, String tooltip, boolean overridable) {
        return new ComponentField(name, FieldType.INT, defaultValue, min, max, tooltip, overridable, null);
    }

    public static ComponentField floatField(String name, float defaultValue, float min, float max, String tooltip, boolean overridable) {
        return new ComponentField(name, FieldType.FLOAT, defaultValue, min, max, tooltip, overridable, null);
    }

    public static ComponentField booleanField(String name, boolean defaultValue, String tooltip, boolean overridable) {
        return new ComponentField(name, FieldType.BOOL, defaultValue, null, null, tooltip, overridable, null);
    }

    public static ComponentField enumField(String name, String defaultValue, List<String> constants, String tooltip, boolean overridable) {
        return new ComponentField(name, FieldType.ENUM, defaultValue, null, null, tooltip, overridable, constants);
    }

    public static ComponentField stringField(String name, String defaultValue, String tooltip, boolean overridable) {
        return new ComponentField(name, FieldType.STRING, defaultValue, null, null, tooltip, overridable, null);
    }

    public boolean isValidValue(Object val) {
        if (val == null) return false;
        switch (type) {
            case INT -> {
                if (!(val instanceof Number num)) return false;
                long l = num.longValue();
                if (min != null && l < min.longValue()) return false;
                if (max != null && l > max.longValue()) return false;
                return true;
            }
            case FLOAT -> {
                if (!(val instanceof Number num)) return false;
                double d = num.doubleValue();
                if (min != null && d < min.doubleValue()) return false;
                if (max != null && d > max.doubleValue()) return false;
                return true;
            }
            case BOOL -> {
                return val instanceof Boolean;
            }
            case ENUM -> {
                if (!(val instanceof String s)) return false;
                return enumConstants.contains(s);
            }
            case STRING -> {
                return val instanceof String;
            }
            default -> {
                return true;
            }
        }
    }
}
