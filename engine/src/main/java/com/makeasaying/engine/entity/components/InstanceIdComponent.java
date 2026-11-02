package com.makeasaying.engine.entity.components;

/**
 * Generational instance ID string associated with an authored entity instance within a room.
 * Example: "moving_platform_1".
 * Captured in snapshots and state hashes for determinism.
 */
public record InstanceIdComponent(String instanceId) {}
