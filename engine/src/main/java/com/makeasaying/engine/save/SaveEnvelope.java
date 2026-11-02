package com.makeasaying.engine.save;

/**
 * Outer envelope for versioned checkpoint save files per M7b brief.
 * Structure: { "version": 1, "gameVersion": "0.1.0", "checksum": "...", "data": { ... } }
 */
public record SaveEnvelope(
    int version,
    String gameVersion,
    String checksum,
    SavePayload data
) {
    public SaveEnvelope {
        if (version <= 0) {
            throw new IllegalArgumentException("Save version must be positive: " + version);
        }
        if (gameVersion == null || gameVersion.isBlank()) {
            throw new IllegalArgumentException("gameVersion must not be blank");
        }
        if (checksum == null || checksum.isBlank()) {
            throw new IllegalArgumentException("checksum must not be blank");
        }
        if (data == null) {
            throw new IllegalArgumentException("data payload must not be null");
        }
    }
}
