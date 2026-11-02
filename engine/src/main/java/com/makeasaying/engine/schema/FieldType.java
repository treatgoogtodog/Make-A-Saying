package com.makeasaying.engine.schema;

/**
 * Supported field data types for component schemas per AGENTS.md M6b brief.
 * Rule (DEC-025): INT is strictly used for discrete offsets, pixel sizes, ticks, and counts;
 * FLOAT is used exclusively for authored speeds (px/s).
 */
public enum FieldType {
    INT,
    FLOAT,
    BOOL,
    ENUM,
    STRING
}
