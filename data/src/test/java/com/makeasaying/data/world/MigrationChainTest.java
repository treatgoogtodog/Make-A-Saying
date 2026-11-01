package com.makeasaying.data.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MigrationChainTest {
    private ObjectMapper mapper;
    private MigrationChain chain;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        chain = new MigrationChain("test_format", 3);

        // v1 -> v2: rename "name" to "title"
        chain.register(1, root -> {
            if (root.has("name")) {
                root.put("title", root.get("name").asText());
                root.remove("name");
            }
        });

        // v2 -> v3: add default "enabled" = true
        chain.register(2, root -> {
            root.put("enabled", true);
        });
    }

    @Test
    void testSuccessfulMigrationChain() {
        ObjectNode root = mapper.createObjectNode();
        root.put("version", 1);
        root.put("name", "Test Item");

        ObjectNode migrated = chain.migrate(root);

        assertEquals(3, migrated.get("version").asInt());
        assertFalse(migrated.has("name"));
        assertEquals("Test Item", migrated.get("title").asText());
        assertTrue(migrated.get("enabled").asBoolean());
    }

    @Test
    void testCurrentVersionNeedsNoMigration() {
        ObjectNode root = mapper.createObjectNode();
        root.put("version", 3);
        root.put("title", "Already V3");

        ObjectNode result = chain.migrate(root);
        assertEquals(3, result.get("version").asInt());
        assertEquals("Already V3", result.get("title").asText());
    }

    @Test
    void testRejectUnknownNewerVersion() {
        ObjectNode root = mapper.createObjectNode();
        root.put("version", 99);

        assertThrows(IllegalArgumentException.class, () -> chain.migrate(root));
    }

    @Test
    void testRejectMissingVersion() {
        ObjectNode root = mapper.createObjectNode();
        root.put("title", "No Version");

        assertThrows(IllegalArgumentException.class, () -> chain.migrate(root));
    }

    @Test
    void testMissingIntermediateStepFailsLoudly() {
        MigrationChain brokenChain = new MigrationChain("broken", 3);
        // Only register v1 -> v2, skip v2 -> v3
        brokenChain.register(1, root -> {});

        ObjectNode root = mapper.createObjectNode();
        root.put("version", 1);

        assertThrows(IllegalStateException.class, () -> brokenChain.migrate(root));
    }
}
