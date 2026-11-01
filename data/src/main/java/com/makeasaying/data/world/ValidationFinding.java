package com.makeasaying.data.world;

/**
 * Single finding produced by RoomValidator per M5a brief.
 * Format: location: message.
 */
public record ValidationFinding(
    ValidationSeverity severity,
    String code,
    String location,
    String message
) {
    public ValidationFinding {
        if (severity == null) throw new IllegalArgumentException("severity must not be null");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        if (location == null) location = "";
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message must not be blank");
    }

    @Override
    public String toString() {
        if (location.isEmpty()) {
            return String.format("[%s][%s] %s", severity, code, message);
        }
        return String.format("[%s][%s] %s: %s", severity, code, location, message);
    }
}
