package com.makeasaying.data.world;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.makeasaying.engine.world.Room;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Saves Room files per M5a brief.
 * Formatted with 2-space indentation, stable key order with version first, trailing LF newline.
 */
public final class RoomWriter {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private RoomWriter() {}

    private static DefaultPrettyPrinter createPrinter() {
        Separators separators = Separators.createDefaultInstance()
            .withObjectFieldValueSpacing(Separators.Spacing.AFTER);
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter(separators);
        printer.indentObjectsWith(new DefaultIndenter("  ", "\n"));
        printer.indentArraysWith(new DefaultIndenter("  ", "\n"));
        return printer;
    }

    public static void save(Room room, Path path) throws IOException {
        save(room, path, RoomLoader.CURRENT_VERSION);
    }

    public static void save(Room room, Path path, int version) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        RoomLoader.RoomData data = RoomLoader.toData(room, version);
        String json = MAPPER.writer(createPrinter()).writeValueAsString(data);
        if (!json.endsWith("\n")) {
            json += "\n";
        }

        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    public static String toJson(Room room, int version) throws IOException {
        RoomLoader.RoomData data = RoomLoader.toData(room, version);
        String json = MAPPER.writer(createPrinter()).writeValueAsString(data);
        if (!json.endsWith("\n")) {
            json += "\n";
        }
        return json;
    }
}
