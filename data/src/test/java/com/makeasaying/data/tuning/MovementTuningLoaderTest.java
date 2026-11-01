package com.makeasaying.data.tuning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeasaying.engine.entity.MovementTuning;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MovementTuningLoaderTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testLoadAuthoredMovementTuning() throws IOException {
        Path path = Path.of("../content/tuning/movement.json");
        if (!Files.exists(path)) path = Path.of("content/tuning/movement.json");

        assertTrue(Files.exists(path), "content/tuning/movement.json must exist");

        MovementTuning tuning = MovementTuningLoader.load(path, 60);
        assertEquals(1, tuning.version());
        assertEquals(900.0f, tuning.groundAccel());
        assertEquals(1200.0f, tuning.groundFriction());
        assertEquals(120.0f, tuning.maxRunSpeed());
        assertEquals(240.0f, tuning.jumpSpeed());
        assertEquals(0.5f, tuning.jumpCutMultiplier());
        assertEquals(6, tuning.coyoteTicks());
        assertEquals(6, tuning.jumpBufferTicks());
        assertEquals(4, tuning.cornerCorrectionPx());
        assertEquals(4, tuning.slopeSnapPx());
        assertEquals(8, tuning.dropThroughTicks());

        // Verify precomputed per-tick values
        assertEquals(2.0f, tuning.maxRunSpeedPerTick(), 0.001f);
        assertEquals(4.0f, tuning.jumpSpeedPerTick(), 0.001f);
    }

    @Test
    void testRejectInvalidTuningValues() {
        String badGravity = """
        {
          "version": 1,
          "groundAccel": 900.0,
          "groundFriction": 1200.0,
          "groundTurnaroundAccel": 1800.0,
          "airAccel": 600.0,
          "airFriction": 300.0,
          "airTurnaroundAccel": 1200.0,
          "maxRunSpeed": 120.0,
          "jumpSpeed": 270.0,
          "jumpCutMultiplier": 0.5,
          "riseGravity": -500.0,
          "fallGravity": 1200.0,
          "maxFallSpeed": 360.0,
          "coyoteTicks": 6,
          "jumpBufferTicks": 6,
          "cornerCorrectionPx": 4
        }
        """;
        assertThrows(IllegalStateException.class, () ->
            MovementTuningLoader.parse(MAPPER.readTree(badGravity), 60, "bad-gravity-test")
        );

        String badJumpCut = """
        {
          "version": 1,
          "groundAccel": 900.0,
          "groundFriction": 1200.0,
          "groundTurnaroundAccel": 1800.0,
          "airAccel": 600.0,
          "airFriction": 300.0,
          "airTurnaroundAccel": 1200.0,
          "maxRunSpeed": 120.0,
          "jumpSpeed": 270.0,
          "jumpCutMultiplier": 1.5,
          "riseGravity": 900.0,
          "fallGravity": 1200.0,
          "maxFallSpeed": 360.0,
          "coyoteTicks": 6,
          "jumpBufferTicks": 6,
          "cornerCorrectionPx": 4
        }
        """;
        assertThrows(IllegalStateException.class, () ->
            MovementTuningLoader.parse(MAPPER.readTree(badJumpCut), 60, "bad-jumpcut-test")
        );
        String badSlopeSnap = """
        {
          "version": 1,
          "groundAccel": 900.0,
          "groundFriction": 1200.0,
          "groundTurnaroundAccel": 1800.0,
          "airAccel": 600.0,
          "airFriction": 300.0,
          "airTurnaroundAccel": 1200.0,
          "maxRunSpeed": 120.0,
          "jumpSpeed": 240.0,
          "jumpCutMultiplier": 0.5,
          "riseGravity": 900.0,
          "fallGravity": 1200.0,
          "maxFallSpeed": 360.0,
          "coyoteTicks": 6,
          "jumpBufferTicks": 6,
          "cornerCorrectionPx": 4,
          "slopeSnapPx": 2
        }
        """;
        assertThrows(IllegalArgumentException.class, () ->
            MovementTuningLoader.parse(MAPPER.readTree(badSlopeSnap), 60, "bad-slopesnap-test")
        );
    }
}
