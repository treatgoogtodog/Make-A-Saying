package com.makeasaying.data.input;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.makeasaying.engine.input.replay.ReplayData;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serializes and deserializes versioned sparse ReplayData per AGENTS.md section 7.4.
 */
public final class ReplayLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private ReplayLoader() {}

    public static ReplayData load(Path path) throws IOException {
        if (!Files.exists(path)) {
            Log.error(LogCategory.DATA, "Replay file does not exist at: %s", path.toAbsolutePath());
            throw new IllegalArgumentException("Replay file not found: " + path);
        }

        ReplayData data = MAPPER.readValue(path.toFile(), ReplayData.class);
        validate(data, path.toString());
        return data;
    }

    public static void save(ReplayData data, Path path) throws IOException {
        validate(data, "in-memory replay data");
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        MAPPER.writer(printer).writeValue(path.toFile(), data);
    }

    public static void validate(ReplayData data, String source) {
        if (data == null) {
            throw new IllegalArgumentException("ReplayData cannot be null in " + source);
        }
        if (data.version() <= 0) {
            throw new IllegalStateException("Invalid replay version in " + source + ": " + data.version());
        }
        if (data.tickRate() <= 0) {
            throw new IllegalStateException("Invalid tick rate in " + source + ": " + data.tickRate());
        }
        if (data.frames() == null) {
            throw new IllegalStateException("Replay frames list cannot be null in " + source);
        }
    }
}
