package com.makeasaying.editor.core.flags;

import com.makeasaying.engine.flags.FlagChangeLogEntry;
import com.makeasaying.engine.flags.FlagDefinition;
import com.makeasaying.engine.flags.FlagRegistry;
import com.makeasaying.engine.flags.FlagScope;
import com.makeasaying.engine.flags.FlagStore;
import com.makeasaying.engine.flags.FlagType;
import com.makeasaying.engine.world.RoomStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlagPanelModelTest {
    private FlagRegistry registry;
    private FlagStore store;
    private FlagPanelModel model;

    @BeforeEach
    void setup() {
        FlagDefinition f1 = new FlagDefinition(0, "game.score", FlagType.INT, FlagScope.GLOBAL, 0, 0, 100, null, "doc");
        FlagDefinition f2 = new FlagDefinition(1, "game.door_open", FlagType.BOOL, FlagScope.ROOM, 0, 0, 1, null, "doc");
        FlagDefinition f3 = new FlagDefinition(2, "puzzle.state", FlagType.ENUM, FlagScope.SESSION, 0, 0, 2, List.of("a", "b", "c"), "doc");
        FlagDefinition f4 = new FlagDefinition(3, "temp.hazard", FlagType.BOOL, FlagScope.ROOM_TEMP, 1, 0, 1, null, "doc");

        registry = new FlagRegistry(List.of(f1, f2, f3, f4));
        store = new FlagStore(registry, new RoomStateStore());
        model = new FlagPanelModel();
    }

    @Test
    void testVisibilityToggle() {
        assertFalse(model.isVisible());
        model.toggleVisible();
        assertTrue(model.isVisible());
        model.toggleVisible();
        assertFalse(model.isVisible());
    }

    @Test
    void testFilterBySearchAndScope() {
        // No filters: returns all 4
        assertEquals(4, model.filter(registry).size());

        // Search for "door"
        model.setSearchFilter("DOOR");
        List<FlagDefinition> doorMatches = model.filter(registry);
        assertEquals(1, doorMatches.size());
        assertEquals("game.door_open", doorMatches.get(0).id());

        // Scope filter ROOM
        model.setSearchFilter("");
        model.setScopeFilter(FlagScope.ROOM);
        List<FlagDefinition> roomMatches = model.filter(registry);
        assertEquals(1, roomMatches.size());
        assertEquals("game.door_open", roomMatches.get(0).id());

        // Scope filter GLOBAL
        model.setScopeFilter(FlagScope.GLOBAL);
        List<FlagDefinition> globalMatches = model.filter(registry);
        assertEquals(1, globalMatches.size());
        assertEquals("game.score", globalMatches.get(0).id());
    }

    @Test
    void testMutationRequestsWhenEditable() {
        model.setEditable(true);

        // Toggle door_open
        model.requestToggle(store, 1, FlagChangeLogEntry.SOURCE_DEBUG_PANEL);
        store.applyPendingChanges("room_01", null, 1L);
        assertEquals(1, store.get(1, "room_01"));

        // Add 10 to score
        model.requestAdd(store, 0, 10, FlagChangeLogEntry.SOURCE_DEBUG_PANEL);
        store.applyPendingChanges("room_01", null, 2L);
        assertEquals(10, store.get(0, "room_01"));
    }

    @Test
    void testMutationRequestsIgnoredWhenReadOnly() {
        model.setEditable(false);

        // Toggle door_open should be ignored
        model.requestToggle(store, 1, FlagChangeLogEntry.SOURCE_DEBUG_PANEL);
        store.applyPendingChanges("room_01", null, 1L);
        assertEquals(0, store.get(1, "room_01"));

        // Add 10 to score should be ignored
        model.requestAdd(store, 0, 10, FlagChangeLogEntry.SOURCE_DEBUG_PANEL);
        store.applyPendingChanges("room_01", null, 2L);
        assertEquals(0, store.get(0, "room_01"));
    }
}
