package com.makeasaying.engine.save;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure data model representing the inner data payload of a checkpoint save.
 * Engine module: strictly zero Jackson, zero file I/O.
 */
public record SavePayload(
    long playtimeTicks,
    String timestampUtc,
    String currentRoomId,
    String savepointInstanceId,
    Map<String, Object> globalFlags,
    Map<String, Map<String, Object>> roomRecords,
    Map<String, Object> reservedSections
) {
    public SavePayload {
        if (currentRoomId == null || currentRoomId.isBlank()) {
            throw new IllegalArgumentException("currentRoomId must not be blank");
        }
        if (savepointInstanceId == null || savepointInstanceId.isBlank()) {
            throw new IllegalArgumentException("savepointInstanceId must not be blank");
        }
        globalFlags = globalFlags != null ? Collections.unmodifiableMap(new LinkedHashMap<>(globalFlags)) : Collections.emptyMap();
        roomRecords = roomRecords != null ? Collections.unmodifiableMap(new LinkedHashMap<>(roomRecords)) : Collections.emptyMap();
        reservedSections = reservedSections != null ? Collections.unmodifiableMap(new LinkedHashMap<>(reservedSections)) : Collections.emptyMap();
    }
}
