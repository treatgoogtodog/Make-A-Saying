package com.makeasaying.data.config;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.makeasaying.engine.config.EngineConfig;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads, validates, and saves global configuration per AGENTS.md section 6 and 8.
 */
public final class ConfigLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private ConfigLoader() {}

    public static EngineConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            Log.error(LogCategory.DATA, "Config file does not exist at: %s", path.toAbsolutePath());
            throw new IllegalArgumentException("Config file not found: " + path);
        }

        EngineConfig config;
        try {
            config = MAPPER.readValue(path.toFile(), EngineConfig.class);
        } catch (Exception e) {
            Log.error(LogCategory.DATA, "Failed to parse config file: %s", e, path.toAbsolutePath());
            throw new IOException("Malformed config file: " + path, e);
        }

        validate(config, path.toString());
        return config;
    }

    public static EngineConfig loadOrDefault(Path path) {
        try {
            if (Files.exists(path)) {
                return load(path);
            }
            Log.warn(LogCategory.DATA, "Config not found at %s. Using default config.", path);
        } catch (Exception e) {
            Log.error(LogCategory.DATA, "Failed loading config from %s. Falling back to default.", e, path);
        }
        return EngineConfig.defaultConfig();
    }

    public static void save(EngineConfig config, Path path) throws IOException {
        validate(config, "in-memory config to save");
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        MAPPER.writer(printer).writeValue(path.toFile(), config);
    }

    public static void validate(EngineConfig config, String source) {
        if (config == null) {
            throw new IllegalArgumentException("Config from " + source + " cannot be null");
        }
        if (config.version() <= 0) {
            throw new IllegalStateException("Invalid config version in " + source + ": " + config.version());
        }
        if (config.virtualWidth() <= 0 || config.virtualHeight() <= 0) {
            throw new IllegalStateException("Virtual dimensions must be positive in " + source +
                ": " + config.virtualWidth() + "x" + config.virtualHeight());
        }
        if (config.tileSize() <= 0) {
            throw new IllegalStateException("Tile size must be positive in " + source + ": " + config.tileSize());
        }
        if (config.bodyCollider() == null) {
            throw new IllegalStateException("Body collider configuration missing in " + source);
        }
        if (config.bodyCollider().width() <= 0 || config.bodyCollider().standingHeight() <= 0) {
            throw new IllegalStateException("Body collider dimensions must be positive in " + source);
        }
    }
}
