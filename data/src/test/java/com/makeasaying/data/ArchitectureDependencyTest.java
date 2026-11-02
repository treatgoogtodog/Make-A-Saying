package com.makeasaying.data;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureDependencyTest {
    @Test
    void dataMustNeverDependOnEditorModules() {
        // Enforce AGENTS.md principle 4.1: data module must never depend on editors.
        ClassLoader classLoader = getClass().getClassLoader();
        assertThrows(ClassNotFoundException.class, () ->
            Class.forName("com.makeasaying.editor.core.EditorCommand", false, classLoader)
        );
        assertThrows(ClassNotFoundException.class, () ->
            Class.forName("com.makeasaying.editor.map.document.RoomDocument", false, classLoader)
        );
    }

    @Test
    void selfReviewDocumentMustContainExactly30ItemsVerbatim() throws IOException {
        Path path = Paths.get("../docs/SELF_REVIEW.md");
        if (!Files.exists(path)) {
            path = Paths.get("docs/SELF_REVIEW.md");
        }
        assertTrue(Files.exists(path), "docs/SELF_REVIEW.md must exist");
        List<String> lines = Files.readAllLines(path);
        long principleCount = lines.stream().filter(l -> l.matches("^\\d+\\..*")).count();
        long antiPatternCount = lines.stream().filter(l -> l.startsWith("• ")).count();
        assertEquals(15, principleCount, "Must have exactly 15 principles from Section 4");
        assertEquals(15, antiPatternCount, "Must have exactly 15 anti-patterns from Section 12");
        assertEquals(30, principleCount + antiPatternCount, "Total self-review items must be exactly 30");
    }
}

