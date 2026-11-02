package com.makeasaying.engine.flags;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Metadata definition for a single compiled flag in the registry.
 * All flag references at runtime resolve to an integer index [0..N-1].
 */
public final class FlagDefinition {
    private final int index;
    private final String id;
    private final FlagType type;
    private final FlagScope scope;
    private final int defaultValue;
    private final int min;
    private final int max;
    private final List<String> enumValues;
    private final String precomputedRoomKey;
    private final String doc;

    public FlagDefinition(
        int index,
        String id,
        FlagType type,
        FlagScope scope,
        int defaultValue,
        int min,
        int max,
        List<String> enumValues,
        String doc
    ) {
        this.index = index;
        this.id = Objects.requireNonNull(id, "Flag ID cannot be null");
        this.type = Objects.requireNonNull(type, "FlagType cannot be null");
        this.scope = Objects.requireNonNull(scope, "FlagScope cannot be null");
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.enumValues = enumValues != null ? Collections.unmodifiableList(enumValues) : Collections.emptyList();
        this.precomputedRoomKey = "flag." + id;
        this.doc = doc != null ? doc : "";
    }

    public int index() {
        return index;
    }

    public String id() {
        return id;
    }

    public FlagType type() {
        return type;
    }

    public FlagScope scope() {
        return scope;
    }

    public int defaultValue() {
        return defaultValue;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public List<String> enumValues() {
        return enumValues;
    }

    public String precomputedRoomKey() {
        return precomputedRoomKey;
    }

    public String doc() {
        return doc;
    }

    public boolean isValidValue(int val) {
        if (type == FlagType.BOOL) {
            return val == 0 || val == 1;
        } else if (type == FlagType.ENUM) {
            return val >= 0 && val < enumValues.size();
        } else {
            return val >= min && val <= max;
        }
    }

    public String formatValue(int val) {
        if (type == FlagType.BOOL) {
            return val != 0 ? "true" : "false";
        } else if (type == FlagType.ENUM) {
            if (val >= 0 && val < enumValues.size()) {
                return enumValues.get(val);
            }
            return String.valueOf(val);
        } else {
            return String.valueOf(val);
        }
    }

    public int parseValue(Object val) {
        if (val == null) {
            throw new IllegalArgumentException("Value cannot be null for flag " + id);
        }
        if (type == FlagType.BOOL) {
            if (val instanceof Boolean b) {
                return b ? 1 : 0;
            }
            if ("true".equalsIgnoreCase(String.valueOf(val))) return 1;
            if ("false".equalsIgnoreCase(String.valueOf(val))) return 0;
            throw new IllegalArgumentException("Invalid boolean value '" + val + "' for flag " + id);
        } else if (type == FlagType.ENUM) {
            String str = String.valueOf(val);
            int idx = enumValues.indexOf(str);
            if (idx >= 0) {
                return idx;
            }
            try {
                int numeric = Integer.parseInt(str);
                if (numeric >= 0 && numeric < enumValues.size()) {
                    return numeric;
                }
            } catch (NumberFormatException ignored) {}
            throw new IllegalArgumentException("Unknown enum value '" + val + "' for flag " + id + ". Valid: " + enumValues);
        } else {
            if (val instanceof Number n) {
                int intVal = n.intValue();
                if (intVal < min || intVal > max) {
                    throw new IllegalArgumentException(String.format("Value %d out of range [%d..%d] for flag %s", intVal, min, max, id));
                }
                return intVal;
            }
            try {
                int intVal = Integer.parseInt(String.valueOf(val));
                if (intVal < min || intVal > max) {
                    throw new IllegalArgumentException(String.format("Value %d out of range [%d..%d] for flag %s", intVal, min, max, id));
                }
                return intVal;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid integer value '" + val + "' for flag " + id);
            }
        }
    }
}
