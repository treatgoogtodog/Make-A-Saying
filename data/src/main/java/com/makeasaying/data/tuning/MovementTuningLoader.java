package com.makeasaying.data.tuning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeasaying.engine.entity.MovementTuning;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads, validates, and converts movement physics tuning parameters from JSON per DEC-010.
 */
public final class MovementTuningLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MovementTuningLoader() {}

    public static MovementTuning load(Path path, int tickRate) throws IOException {
        if (!Files.exists(path)) {
            Log.error(LogCategory.DATA, "Movement tuning file not found: %s", path.toAbsolutePath());
            throw new IllegalArgumentException("Movement tuning file not found: " + path);
        }

        JsonNode root = MAPPER.readTree(path.toFile());
        return parse(root, tickRate, path.toString());
    }

    public static MovementTuning parse(JsonNode root, int tickRate, String source) {
        if (!root.has("version") || root.get("version").asInt() <= 0) {
            throw new IllegalStateException("Missing or invalid version in " + source);
        }

        int version = root.get("version").asInt();
        float groundAccel = (float) root.get("groundAccel").asDouble();
        float groundFriction = (float) root.get("groundFriction").asDouble();
        float groundTurnaroundAccel = (float) root.get("groundTurnaroundAccel").asDouble();
        float airAccel = (float) root.get("airAccel").asDouble();
        float airFriction = (float) root.get("airFriction").asDouble();
        float airTurnaroundAccel = (float) root.get("airTurnaroundAccel").asDouble();
        float maxRunSpeed = (float) root.get("maxRunSpeed").asDouble();
        float jumpSpeed = (float) root.get("jumpSpeed").asDouble();
        float jumpCutMultiplier = (float) root.get("jumpCutMultiplier").asDouble();
        float riseGravity = (float) root.get("riseGravity").asDouble();
        float fallGravity = (float) root.get("fallGravity").asDouble();
        float maxFallSpeed = (float) root.get("maxFallSpeed").asDouble();
        int coyoteTicks = root.get("coyoteTicks").asInt();
        int jumpBufferTicks = root.get("jumpBufferTicks").asInt();
        int cornerCorrectionPx = root.get("cornerCorrectionPx").asInt();

        // Range checks
        if (groundAccel <= 0 || groundFriction <= 0 || groundTurnaroundAccel <= 0) {
            throw new IllegalStateException("Ground accelerations must be positive in " + source);
        }
        if (airAccel <= 0 || airFriction <= 0 || airTurnaroundAccel <= 0) {
            throw new IllegalStateException("Air accelerations must be positive in " + source);
        }
        if (maxRunSpeed <= 0) {
            throw new IllegalStateException("maxRunSpeed must be positive in " + source);
        }
        if (jumpSpeed <= 0) {
            throw new IllegalStateException("jumpSpeed must be positive in " + source);
        }
        if (jumpCutMultiplier < 0.0f || jumpCutMultiplier > 1.0f) {
            throw new IllegalStateException("jumpCutMultiplier must be between 0.0 and 1.0 in " + source);
        }
        if (riseGravity <= 0 || fallGravity <= 0) {
            throw new IllegalStateException("Gravities must be positive in " + source);
        }
        if (maxFallSpeed <= 0) {
            throw new IllegalStateException("maxFallSpeed must be positive in " + source);
        }
        if (coyoteTicks < 0 || jumpBufferTicks < 0) {
            throw new IllegalStateException("Coyote and buffer ticks cannot be negative in " + source);
        }

        return MovementTuning.fromHumanUnits(
            version,
            groundAccel,
            groundFriction,
            groundTurnaroundAccel,
            airAccel,
            airFriction,
            airTurnaroundAccel,
            maxRunSpeed,
            jumpSpeed,
            jumpCutMultiplier,
            riseGravity,
            fallGravity,
            maxFallSpeed,
            coyoteTicks,
            jumpBufferTicks,
            cornerCorrectionPx,
            tickRate
        );
    }
}
