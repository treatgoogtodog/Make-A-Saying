package com.makeasaying.data.world;

import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomExit;
import com.makeasaying.engine.world.SpawnPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cross-room integrity validator per M5b brief.
 * Validates links, bounds, spawn overlaps, and topological dead-ends/orphans across rooms.
 */
public final class CrossRoomValidator {
    public static final String ERR_UNKNOWN_TARGET_ROOM = "ERR_UNKNOWN_TARGET_ROOM";
    public static final String ERR_UNKNOWN_TARGET_SPAWN = "ERR_UNKNOWN_TARGET_SPAWN";
    public static final String ERR_DUPLICATE_EXIT_ID = "ERR_DUPLICATE_EXIT_ID";
    public static final String ERR_INVALID_EXIT_BOUNDS = "ERR_INVALID_EXIT_BOUNDS";
    public static final String WARN_EXIT_INSIDE_SOLID = "WARN_EXIT_INSIDE_SOLID";
    public static final String WARN_SPAWN_INSIDE_EXIT = "WARN_SPAWN_INSIDE_EXIT";
    public static final String INFO_ORPHAN_ROOM = "INFO_ORPHAN_ROOM";
    public static final String INFO_DEAD_END_ROOM = "INFO_DEAD_END_ROOM";

    private final String startRoomId;

    public CrossRoomValidator(String startRoomId) {
        this.startRoomId = startRoomId;
    }

    public CrossRoomValidator() {
        this("test_01");
    }

    public List<ValidationFinding> validate(Map<String, Room> rooms, Map<String, TileGrid> tileGrids) {
        List<ValidationFinding> findings = new ArrayList<>();
        if (rooms == null || rooms.isEmpty()) return findings;

        // Track incoming exits per room
        Map<String, Integer> incomingExits = new HashMap<>();
        for (String rId : rooms.keySet()) {
            incomingExits.put(rId, 0);
        }

        for (Map.Entry<String, Room> entry : rooms.entrySet()) {
            String roomId = entry.getKey();
            Room room = entry.getValue();
            TileGrid grid = (tileGrids != null) ? tileGrids.get(roomId) : null;
            int roomPixelWidth = room.width() * (grid != null ? grid.getTileSize() : 16);
            int roomPixelHeight = room.height() * (grid != null ? grid.getTileSize() : 16);
            int tileSize = (grid != null) ? grid.getTileSize() : 16;

            Set<String> exitIds = new HashSet<>();
            List<RoomExit> exits = room.exits();

            for (RoomExit exit : exits) {
                String loc = "room " + roomId + ", exit " + exit.id();

                // 1. Duplicate exit ID
                if (!exitIds.add(exit.id())) {
                    findings.add(new ValidationFinding(ValidationSeverity.ERROR, ERR_DUPLICATE_EXIT_ID,
                        loc, "Duplicate exit id '" + exit.id() + "' in room " + roomId));
                }

                // 2. Invalid exit bounds
                if (exit.x() < 0 || exit.y() < 0 ||
                    exit.x() + exit.width() > roomPixelWidth ||
                    exit.y() + exit.height() > roomPixelHeight ||
                    exit.width() <= 0 || exit.height() <= 0) {
                    findings.add(new ValidationFinding(ValidationSeverity.ERROR, ERR_INVALID_EXIT_BOUNDS,
                        loc, String.format("Exit bounds [%d, %d, %d, %d] outside room boundaries %dx%d",
                            exit.x(), exit.y(), exit.width(), exit.height(), roomPixelWidth, roomPixelHeight)));
                }

                // 3. Target room exists
                Room targetRoom = rooms.get(exit.targetRoom());
                if (targetRoom == null) {
                    findings.add(new ValidationFinding(ValidationSeverity.ERROR, ERR_UNKNOWN_TARGET_ROOM,
                        loc, "Target room '" + exit.targetRoom() + "' not found"));
                } else {
                    // Count incoming exit to target room
                    incomingExits.put(exit.targetRoom(), incomingExits.getOrDefault(exit.targetRoom(), 0) + 1);

                    // 4. Target spawn exists in target room
                    boolean spawnFound = false;
                    for (SpawnPoint sp : targetRoom.spawnPoints()) {
                        if (sp.id().equals(exit.targetSpawn())) {
                            spawnFound = true;
                            break;
                        }
                    }
                    if (!spawnFound) {
                        findings.add(new ValidationFinding(ValidationSeverity.ERROR, ERR_UNKNOWN_TARGET_SPAWN,
                            loc, String.format("Target spawn '%s' not found in room '%s'", exit.targetSpawn(), exit.targetRoom())));
                    }
                }

                // 5. Exit inside solid check
                if (grid != null && exit.width() > 0 && exit.height() > 0) {
                    int minTx = Math.max(0, exit.x() / tileSize);
                    int maxTx = Math.min(grid.getWidth() - 1, (exit.x() + exit.width() - 1) / tileSize);
                    int minTy = Math.max(0, exit.y() / tileSize);
                    int maxTy = Math.min(grid.getHeight() - 1, (exit.y() + exit.height() - 1) / tileSize);

                    boolean allSolid = true;
                    for (int ty = minTy; ty <= maxTy; ty++) {
                        for (int tx = minTx; tx <= maxTx; tx++) {
                            if (!grid.isSolid(tx, ty)) {
                                allSolid = false;
                                break;
                            }
                        }
                        if (!allSolid) break;
                    }

                    if (allSolid) {
                        findings.add(new ValidationFinding(ValidationSeverity.WARNING, WARN_EXIT_INSIDE_SOLID,
                            loc, "Exit zone is completely covered by solid collision tiles"));
                    }
                }

                // 6. Spawn inside exit check (in current room)
                for (SpawnPoint sp : room.spawnPoints()) {
                    // Check if player bounding box (12x36) placed at spawn point overlaps exit zone
                    if (exit.overlaps(sp.x(), sp.y(), 12, 36)) {
                        findings.add(new ValidationFinding(ValidationSeverity.WARNING, WARN_SPAWN_INSIDE_EXIT,
                            loc, String.format("Spawn point '%s' at (%d, %d) overlaps exit zone", sp.id(), sp.x(), sp.y())));
                    }
                }
            }

            // 7. Dead end check: has incoming exits, but 0 outgoing exits
            // (We will evaluate after all rooms are scanned)
        }

        // Topological analysis
        for (Map.Entry<String, Room> entry : rooms.entrySet()) {
            String roomId = entry.getKey();
            Room room = entry.getValue();
            int inCount = incomingExits.getOrDefault(roomId, 0);

            // Orphan room: 0 incoming exits and not the designated start room
            if (inCount == 0 && (startRoomId == null || !startRoomId.equals(roomId))) {
                findings.add(new ValidationFinding(ValidationSeverity.INFO, INFO_ORPHAN_ROOM,
                    "room " + roomId, "Room has no incoming exits from any other room"));
            }

            // Dead-end room: has incoming exits, but no outgoing exits
            if (inCount > 0 && room.exits().isEmpty()) {
                findings.add(new ValidationFinding(ValidationSeverity.INFO, INFO_DEAD_END_ROOM,
                    "room " + roomId, "Room has incoming exits but no outgoing exits"));
            }
        }

        return findings;
    }
}
