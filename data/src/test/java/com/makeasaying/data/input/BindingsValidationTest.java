package com.makeasaying.data.input;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeasaying.engine.input.ActionDefinition;
import com.makeasaying.engine.input.ActionRegistry;
import com.makeasaying.engine.input.ContextBindings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BindingsValidationTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private ActionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ActionRegistry();
        registry.register(ActionDefinition.button("jump"));
        registry.register(ActionDefinition.button("attack"));
        registry.register(ActionDefinition.button("cancel", true)); // reserved
        registry.register(ActionDefinition.button("pause", true));  // reserved
    }

    @Test
    void testLoadAuthoredActionsAndBindings() throws IOException {
        Path actionsPath = Path.of("../content/actions.json");
        if (!Files.exists(actionsPath)) actionsPath = Path.of("content/actions.json");
        Path bindingsPath = Path.of("../content/bindings.json");
        if (!Files.exists(bindingsPath)) bindingsPath = Path.of("content/bindings.json");

        assertTrue(Files.exists(actionsPath));
        assertTrue(Files.exists(bindingsPath));

        ActionRegistry loadedRegistry = ActionLoader.load(actionsPath);
        assertNotNull(loadedRegistry);
        assertTrue(loadedRegistry.contains("jump"));
        assertTrue(loadedRegistry.contains("move_x"));

        ContextBindings loadedBindings = BindingsLoader.load(bindingsPath, loadedRegistry);
        assertNotNull(loadedBindings);
        assertTrue(loadedBindings.hasBindingForAction("gameplay", "jump"));
        assertTrue(loadedBindings.hasBindingForAction("global", "pause"));
    }

    @Test
    void testRejectUnknownKeyName() {
        String json = """
        {
          "version": 1,
          "contexts": {
            "gameplay": {
              "jump": ["KEY_MADE_UP_FOOBAR"],
              "cancel": ["KEY_ESCAPE"]
            },
            "global": {
              "pause": ["KEY_ESCAPE"]
            }
          }
        }
        """;
        assertThrows(IllegalStateException.class, () ->
            BindingsLoader.parse(MAPPER.readTree(json), registry, "test-source")
        );
    }

    @Test
    void testRejectUnknownAction() {
        String json = """
        {
          "version": 1,
          "contexts": {
            "gameplay": {
              "unknown_action_xyz": ["KEY_SPACE"],
              "cancel": ["KEY_ESCAPE"]
            },
            "global": {
              "pause": ["KEY_ESCAPE"]
            }
          }
        }
        """;
        assertThrows(IllegalStateException.class, () ->
            BindingsLoader.parse(MAPPER.readTree(json), registry, "test-source")
        );
    }

    @Test
    void testRejectDuplicateKeyInSameContext() {
        String json = """
        {
          "version": 1,
          "contexts": {
            "gameplay": {
              "jump": ["KEY_SPACE"],
              "attack": ["KEY_SPACE"],
              "cancel": ["KEY_ESCAPE"]
            },
            "global": {
              "pause": ["KEY_ESCAPE"]
            }
          }
        }
        """;
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            BindingsLoader.parse(MAPPER.readTree(json), registry, "test-source")
        );
        assertTrue(ex.getMessage().contains("Duplicate binding"));
    }

    @Test
    void testRejectUnboundReservedAction() {
        // "cancel" is reserved, but not bound anywhere in this JSON
        String json = """
        {
          "version": 1,
          "contexts": {
            "gameplay": {
              "jump": ["KEY_SPACE"]
            },
            "global": {
              "pause": ["KEY_ESCAPE"]
            }
          }
        }
        """;
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            BindingsLoader.parse(MAPPER.readTree(json), registry, "test-source")
        );
        assertTrue(ex.getMessage().contains("Reserved action 'cancel'"));
    }
}
