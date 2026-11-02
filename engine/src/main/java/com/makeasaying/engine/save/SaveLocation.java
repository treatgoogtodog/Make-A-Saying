package com.makeasaying.engine.save;

import java.io.IOException;

/**
 * Storage abstraction interface for checkpoint save files.
 * Implementations handle OS per-user paths (game) or in-memory virtual storage (tests/replay).
 */
public interface SaveLocation {
    boolean existsMain(String slot);
    boolean existsBak(String slot);

    String readMain(String slot) throws IOException;
    String readBak(String slot) throws IOException;

    void writeTemp(String slot, String content) throws IOException;
    void copyToBak(String slot) throws IOException;
    void atomicMoveTempToMain(String slot) throws IOException;

    /**
     * Moves a corrupted main file to a unique collision-free quarantine path:
     * slot.corrupt, slot.corrupt.1, slot.corrupt.2, etc. Never deletes or overwrites existing corrupt files.
     */
    void quarantineCorrupt(String slot) throws IOException;
}
