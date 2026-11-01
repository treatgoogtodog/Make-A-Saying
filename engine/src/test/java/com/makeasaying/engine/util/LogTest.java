package com.makeasaying.engine.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogTest {

    @BeforeEach
    void setUp() {
        Log.clear();
        Log.setBufferCapacity(1000);
        Log.setMinLevel(LogLevel.DEBUG);
        for (LogCategory cat : LogCategory.values()) {
            Log.setCategoryEnabled(cat, true);
        }
    }

    @Test
    void testBasicLoggingAndRingBuffer() {
        Log.info(LogCategory.GENERAL, "Hello %s", "World");
        List<LogEntry> entries = Log.getEntries();
        assertEquals(1, entries.size());

        LogEntry entry = entries.get(0);
        assertEquals(LogLevel.INFO, entry.level());
        assertEquals(LogCategory.GENERAL, entry.category());
        assertEquals("Hello World", entry.message());
        assertNull(entry.throwable());
        assertTrue(entry.formatted().contains("Hello World"));
    }

    @Test
    void testLogLevelFiltering() {
        Log.setMinLevel(LogLevel.WARN);

        Log.debug(LogCategory.ENGINE, "Debug message");
        Log.info(LogCategory.ENGINE, "Info message");
        Log.warn(LogCategory.ENGINE, "Warn message");
        Log.error(LogCategory.ENGINE, "Error message");

        List<LogEntry> entries = Log.getEntries();
        assertEquals(2, entries.size());
        assertEquals(LogLevel.WARN, entries.get(0).level());
        assertEquals(LogLevel.ERROR, entries.get(1).level());
    }

    @Test
    void testCategoryToggling() {
        Log.setCategoryEnabled(LogCategory.PHYSICS, false);

        Log.info(LogCategory.PHYSICS, "Physics disabled message");
        Log.info(LogCategory.RENDER, "Render message");

        List<LogEntry> entries = Log.getEntries();
        assertEquals(1, entries.size());
        assertEquals(LogCategory.RENDER, entries.get(0).category());
    }

    @Test
    void testBufferCapacityLimit() {
        Log.setBufferCapacity(5);
        for (int i = 0; i < 10; i++) {
            Log.info(LogCategory.GENERAL, "Message %d", i);
        }

        List<LogEntry> entries = Log.getEntries();
        assertEquals(5, entries.size());
        assertEquals("Message 5", entries.get(0).message());
        assertEquals("Message 9", entries.get(4).message());
    }

    @Test
    void testLogSink() {
        List<LogEntry> sinkReceived = new ArrayList<>();
        Log.addSink(sinkReceived::add);

        Log.warn(LogCategory.DATA, "Warning to sink");

        assertEquals(1, sinkReceived.size());
        assertEquals("Warning to sink", sinkReceived.get(0).message());

        Log.removeSink(sinkReceived::add);
    }
}
