package com.makeasaying.editor.map.validator;

import com.makeasaying.data.world.RoomValidator;
import com.makeasaying.data.world.ValidationFinding;
import com.makeasaying.data.world.ValidationSeverity;
import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.engine.world.RoomLayerType;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class EditorValidatorPresenterTest {

    @Test
    public void testDebounceInterval() {
        EditorValidatorPresenter presenter = new EditorValidatorPresenter(new RoomValidator(), 30);
        RoomDocument doc = new RoomDocument("valid_room", 10, 10);

        presenter.update(0, doc, Collections.emptyMap());
        assertTrue(presenter.getErrors().isEmpty(), "Initially valid room must produce no errors");

        // Frame 10: debounced, no re-evaluation
        doc.setId("invalid ID with spaces");
        presenter.update(10, doc, Collections.emptyMap());
        assertFalse(presenter.getErrors().stream().anyMatch(f -> f.code().equals("ERR_INVALID_ID")),
            "Should not re-validate before debounce interval expires");

        // Frame 35: interval exceeded, re-evaluates
        presenter.update(35, doc, Collections.emptyMap());
        assertTrue(presenter.getErrors().stream().anyMatch(f -> f.code().equals("ERR_INVALID_ID")),
            "Must re-validate after debounce interval");
    }

    @Test
    public void testParseLocationTarget() {
        EditorValidatorPresenter presenter = new EditorValidatorPresenter();

        ValidationFinding cellFinding = new ValidationFinding(
            ValidationSeverity.ERROR,
            "ERR_UNKNOWN_TILE",
            "room test_01, layer MAIN (12, 8)",
            "Unknown tile token"
        );
        EditorValidatorPresenter.LocationTarget target = presenter.parseLocationTarget(cellFinding);
        assertNotNull(target);
        assertEquals(12, target.tileX());
        assertEquals(8, target.tileY());
        assertEquals(RoomLayerType.MAIN, target.layer());

        ValidationFinding spawnFinding = new ValidationFinding(
            ValidationSeverity.WARNING,
            "WARN_SPAWN_INSIDE_SOLID",
            "room test_01, spawn default (64, 48)",
            "Spawn point inside solid tile"
        );
        EditorValidatorPresenter.LocationTarget spawnTarget = presenter.parseLocationTarget(spawnFinding);
        assertNotNull(spawnTarget);
        assertEquals(4, spawnTarget.tileX()); // 64 / 16 = 4
        assertEquals(3, spawnTarget.tileY()); // 48 / 16 = 3
    }
}
