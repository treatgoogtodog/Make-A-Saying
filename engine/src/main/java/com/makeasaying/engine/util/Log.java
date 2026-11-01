package com.makeasaying.engine.util;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Loud, category-toggled logger per AGENTS.md sections 4 (rule 7) and 7.17.
 * Supports ring-buffer log history for in-game console/overlay and category filtering.
 */
public final class Log {
    private static final int DEFAULT_BUFFER_CAPACITY = 1000;
    private static final Set<LogCategory> ENABLED_CATEGORIES = Collections.synchronizedSet(
        EnumSet.allOf(LogCategory.class)
    );
    private static final ArrayDeque<LogEntry> BUFFER = new ArrayDeque<>(DEFAULT_BUFFER_CAPACITY);
    private static final List<Consumer<LogEntry>> SINKS = new ArrayList<>();
    private static LogLevel minLevel = LogLevel.DEBUG;
    private static int bufferCapacity = DEFAULT_BUFFER_CAPACITY;

    private Log() {}

    public static synchronized void setMinLevel(LogLevel level) {
        if (level != null) {
            minLevel = level;
        }
    }

    public static synchronized LogLevel getMinLevel() {
        return minLevel;
    }

    public static void setCategoryEnabled(LogCategory category, boolean enabled) {
        if (category == null) return;
        if (enabled) {
            ENABLED_CATEGORIES.add(category);
        } else {
            ENABLED_CATEGORIES.remove(category);
        }
    }

    public static boolean isCategoryEnabled(LogCategory category) {
        return category != null && ENABLED_CATEGORIES.contains(category);
    }

    public static synchronized void addSink(Consumer<LogEntry> sink) {
        if (sink != null && !SINKS.contains(sink)) {
            SINKS.add(sink);
        }
    }

    public static synchronized void removeSink(Consumer<LogEntry> sink) {
        SINKS.remove(sink);
    }

    public static synchronized void clear() {
        BUFFER.clear();
    }

    public static synchronized List<LogEntry> getEntries() {
        return new ArrayList<>(BUFFER);
    }

    public static synchronized void setBufferCapacity(int capacity) {
        bufferCapacity = Math.max(1, capacity);
        while (BUFFER.size() > bufferCapacity) {
            BUFFER.removeFirst();
        }
    }

    public static void debug(LogCategory category, String message, Object... args) {
        log(LogLevel.DEBUG, category, message, null, args);
    }

    public static void info(LogCategory category, String message, Object... args) {
        log(LogLevel.INFO, category, message, null, args);
    }

    public static void warn(LogCategory category, String message, Object... args) {
        log(LogLevel.WARN, category, message, null, args);
    }

    public static void error(LogCategory category, String message, Throwable throwable, Object... args) {
        log(LogLevel.ERROR, category, message, throwable, args);
    }

    public static void error(LogCategory category, String message, Object... args) {
        log(LogLevel.ERROR, category, message, null, args);
    }

    private static synchronized void log(LogLevel level, LogCategory category, String message, Throwable throwable, Object... args) {
        if (level.ordinal() < minLevel.ordinal()) {
            return;
        }
        if (!isCategoryEnabled(category)) {
            return;
        }

        String formattedMessage = (args != null && args.length > 0)
            ? String.format(message, args)
            : message;

        LogEntry entry = new LogEntry(Instant.now(), level, category, formattedMessage, throwable);

        if (BUFFER.size() >= bufferCapacity) {
            BUFFER.removeFirst();
        }
        BUFFER.addLast(entry);

        if (level == LogLevel.ERROR) {
            System.err.println(entry.formatted());
            if (throwable != null) {
                throwable.printStackTrace(System.err);
            }
        } else {
            System.out.println(entry.formatted());
        }

        for (Consumer<LogEntry> sink : SINKS) {
            try {
                sink.accept(entry);
            } catch (Exception e) {
                System.err.println("Error in log sink: " + e.getMessage());
            }
        }
    }
}
