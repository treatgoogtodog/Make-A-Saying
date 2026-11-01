package com.makeasaying.engine.entity;

/**
 * Movement physics tuning parameters per AGENTS.md section 7.5 and DEC-010.
 * Holds human units and precomputes per-tick values once upon creation.
 * Simulation systems strictly read per-tick values.
 */
public record MovementTuning(
    int version,
    float groundAccel,
    float groundFriction,
    float groundTurnaroundAccel,
    float airAccel,
    float airFriction,
    float airTurnaroundAccel,
    float maxRunSpeed,
    float jumpSpeed,
    float jumpCutMultiplier,
    float riseGravity,
    float fallGravity,
    float maxFallSpeed,
    int coyoteTicks,
    int jumpBufferTicks,
    int cornerCorrectionPx,
    int slopeSnapPx,
    int dropThroughTicks,

    // Precomputed per-tick values
    float groundAccelPerTick,
    float groundFrictionPerTick,
    float groundTurnaroundPerTick,
    float airAccelPerTick,
    float airFrictionPerTick,
    float airTurnaroundPerTick,
    float maxRunSpeedPerTick,
    float jumpSpeedPerTick,
    float riseGravityPerTick,
    float fallGravityPerTick,
    float maxFallSpeedPerTick
) {
    public static final int CURRENT_VERSION = 1;

    public static MovementTuning fromHumanUnits(
        int version,
        float groundAccel,
        float groundFriction,
        float groundTurnaroundAccel,
        float airAccel,
        float airFriction,
        float airTurnaroundAccel,
        float maxRunSpeed,
        float jumpSpeed,
        float jumpCutMultiplier,
        float riseGravity,
        float fallGravity,
        float maxFallSpeed,
        int coyoteTicks,
        int jumpBufferTicks,
        int cornerCorrectionPx,
        int slopeSnapPx,
        int dropThroughTicks,
        int tickRate
    ) {
        float rate = (float) tickRate;
        float rateSq = rate * rate;
        float maxRunSpeedPerTick = maxRunSpeed / rate;

        int minSlopeSnapPx = (int) Math.ceil(maxRunSpeedPerTick) + 1;
        if (slopeSnapPx < minSlopeSnapPx) {
            throw new IllegalArgumentException("slopeSnapPx (" + slopeSnapPx + ") must be at least ceil(maxRunSpeedPerTick) + 1 (" + minSlopeSnapPx + ")");
        }
        if (dropThroughTicks < 1) {
            throw new IllegalArgumentException("dropThroughTicks must be positive");
        }

        return new MovementTuning(
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
            slopeSnapPx,
            dropThroughTicks,
            groundAccel / rateSq,
            groundFriction / rateSq,
            groundTurnaroundAccel / rateSq,
            airAccel / rateSq,
            airFriction / rateSq,
            airTurnaroundAccel / rateSq,
            maxRunSpeedPerTick,
            jumpSpeed / rate,
            riseGravity / rateSq,
            fallGravity / rateSq,
            maxFallSpeed / rate
        );
    }

    public static MovementTuning fromHumanUnits(
        int version,
        float groundAccel,
        float groundFriction,
        float groundTurnaroundAccel,
        float airAccel,
        float airFriction,
        float airTurnaroundAccel,
        float maxRunSpeed,
        float jumpSpeed,
        float jumpCutMultiplier,
        float riseGravity,
        float fallGravity,
        float maxFallSpeed,
        int coyoteTicks,
        int jumpBufferTicks,
        int cornerCorrectionPx,
        int tickRate
    ) {
        return fromHumanUnits(
            version, groundAccel, groundFriction, groundTurnaroundAccel,
            airAccel, airFriction, airTurnaroundAccel, maxRunSpeed, jumpSpeed,
            jumpCutMultiplier, riseGravity, fallGravity, maxFallSpeed,
            coyoteTicks, jumpBufferTicks, cornerCorrectionPx, 4, 8, tickRate
        );
    }

    public static MovementTuning defaultTuning(int tickRate) {
        return fromHumanUnits(
            CURRENT_VERSION,
            900.0f,
            1200.0f,
            1800.0f,
            600.0f,
            300.0f,
            1200.0f,
            120.0f,
            240.0f,
            0.5f,
            900.0f,
            1200.0f,
            360.0f,
            6,
            6,
            4,
            4,
            8,
            tickRate
        );
    }
}
