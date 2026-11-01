package com.makeasaying.data.world;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.makeasaying.engine.world.Tileset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Saves Tileset files per M5a brief.
 * Formatted with 2-space indentation, stable key order with version first, trailing LF newline.
 */
public final class TilesetWriter {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private TilesetWriter() {}

    private static DefaultPrettyPrinter createPrinter() {
        Separators separators = Separators.createDefaultInstance()
            .withObjectFieldValueSpacing(Separators.Spacing.AFTER);
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter(separators);
        printer.indentObjectsWith(new DefaultIndenter("  ", "\n"));
        printer.indentArraysWith(new DefaultIndenter("  ", "\n"));
        return printer;
    }

    public static void save(Tileset tileset, Path path) throws IOException {
        save(tileset, path, TilesetLoader.CURRENT_VERSION);
    }

    public static void save(Tileset tileset, Path path, int version) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        TilesetLoader.TilesetData data = TilesetLoader.toData(tileset, version);
        String json = MAPPER.writer(createPrinter()).writeValueAsString(data);
        if (!json.endsWith("\n")) {
            json += "\n";
        }

        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    public static String toJson(Tileset tileset, int version) throws IOException {
        TilesetLoader.TilesetData data = TilesetLoader.toData(tileset, version);
        String json = MAPPER.writer(createPrinter()).writeValueAsString(data);
        if (!json.endsWith("\n")) {
            json += "\n";
        }
        return json;
    }
}
