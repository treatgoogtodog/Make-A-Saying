package com.makeasaying.data.flags;

import com.fasterxml.jackson.databind.JsonNode;
import com.makeasaying.data.world.RoomLoader;
import com.makeasaying.data.world.ValidationFinding;
import com.makeasaying.data.world.ValidationSeverity;
import com.makeasaying.engine.flags.FlagDefinition;
import com.makeasaying.engine.flags.FlagRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure index tracking flag readers and writers across authored content per M7a brief.
 * Reports INFO findings for unused or unwritten flags.
 */
public final class FlagUsageIndex {
    private final Map<String, List<String>> readers = new HashMap<>();
    private final Map<String, List<String>> writers = new HashMap<>();

    public FlagUsageIndex() {}

    public static FlagUsageIndex build(FlagRegistry registry, List<RoomLoader.RoomData> rooms) {
        FlagUsageIndex index = new FlagUsageIndex();
        if (registry == null) return index;

        for (FlagDefinition def : registry.getAll()) {
            index.readers.put(def.id(), new ArrayList<>());
            index.writers.put(def.id(), new ArrayList<>());
        }

        if (rooms != null) {
            for (RoomLoader.RoomData room : rooms) {
                if (room.entities() != null) {
                    for (RoomLoader.EntityInstanceData e : room.entities()) {
                        if (e.spawnCondition() != null) {
                            List<String> referencedFlags = extractFlagReferences(e.spawnCondition());
                            for (String fId : referencedFlags) {
                                index.addReader(fId, "room " + room.id() + ", entity " + e.instanceId());
                            }
                        }
                    }
                }

                if (room.exits() != null) {
                    for (RoomLoader.RoomExitData exit : room.exits()) {
                        if (exit.condition() != null) {
                            List<String> referencedFlags = extractFlagReferences(exit.condition());
                            for (String fId : referencedFlags) {
                                index.addReader(fId, "room " + room.id() + ", exit " + exit.id());
                            }
                        }
                    }
                }
            }
        }

        return index;
    }

    public void addReader(String flagId, String location) {
        readers.computeIfAbsent(flagId, k -> new ArrayList<>()).add(location);
    }

    public void addWriter(String flagId, String location) {
        writers.computeIfAbsent(flagId, k -> new ArrayList<>()).add(location);
    }

    public List<String> getReaders(String flagId) {
        return readers.getOrDefault(flagId, Collections.emptyList());
    }

    public List<String> getWriters(String flagId) {
        return writers.getOrDefault(flagId, Collections.emptyList());
    }

    public List<ValidationFinding> generateFindings(FlagRegistry registry) {
        List<ValidationFinding> findings = new ArrayList<>();
        if (registry == null) return findings;

        for (FlagDefinition def : registry.getAll()) {
            String fId = def.id();
            List<String> rList = getReaders(fId);
            List<String> wList = getWriters(fId);

            String loc = "content/flags.json:flag " + fId;

            if (rList.isEmpty()) {
                findings.add(new ValidationFinding(
                    ValidationSeverity.INFO,
                    "FLAG_INFO_UNREAD",
                    loc,
                    String.format("Flag '%s' is declared in flags.json but is never read by any condition", fId)
                ));
            } else if (wList.isEmpty()) {
                findings.add(new ValidationFinding(
                    ValidationSeverity.INFO,
                    "FLAG_INFO_UNWRITTEN",
                    loc,
                    String.format("Flag '%s' is read by %d location(s) %s but has no declared writer",
                        fId, rList.size(), rList)
                ));
            }
        }

        return findings;
    }

    public static List<String> extractFlagReferences(JsonNode conditionNode) {
        List<String> flags = new ArrayList<>();
        collectFlags(conditionNode, flags);
        return flags;
    }

    private static void collectFlags(JsonNode node, List<String> flags) {
        if (node == null || !node.isObject()) return;

        if (node.has("flag")) {
            flags.add(node.get("flag").asText());
        }
        if (node.has("flag_set")) {
            flags.add(node.get("flag_set").asText());
        }

        if (node.has("all") && node.get("all").isArray()) {
            for (JsonNode child : node.get("all")) {
                collectFlags(child, flags);
            }
        }
        if (node.has("any") && node.get("any").isArray()) {
            for (JsonNode child : node.get("any")) {
                collectFlags(child, flags);
            }
        }
        if (node.has("not")) {
            collectFlags(node.get("not"), flags);
        }
    }
}
