package com.makeasaying.engine.save;

import java.time.Instant;

/**
 * Injected clock interface for ISO-8601 timestamps in save files.
 * Allows test saves to produce deterministic, byte-reproducible JSON envelopes.
 */
@FunctionalInterface
public interface SaveClock {
    String nowUtc();

    static SaveClock systemUtc() {
        return () -> Instant.now().toString();
    }

    static SaveClock fixed(String fixedIsoTimestamp) {
        return () -> fixedIsoTimestamp;
    }
}
