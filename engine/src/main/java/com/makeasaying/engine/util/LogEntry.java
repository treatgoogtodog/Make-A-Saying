package com.makeasaying.engine.util;

import java.time.Instant;

/**
 * An immutable log entry stored in the log history ring buffer.
 */
public record LogEntry(
    Instant timestamp,
    LogLevel level,
    LogCategory category,
    String message,
    Throwable throwable
) {
    public String formatted() {
        if (throwable != null) {
            return String.format("[%s][%s][%s] %s (Exception: %s)",
                timestamp, level, category, message, throwable.getMessage());
        }
        return String.format("[%s][%s][%s] %s",
            timestamp, level, category, message);
    }
}
