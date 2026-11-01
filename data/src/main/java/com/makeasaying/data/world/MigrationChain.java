package com.makeasaying.data.world;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

import java.util.HashMap;
import java.util.Map;

/**
 * Version migration mechanism per Principle 4.6 and M5a brief.
 * Migrates parsed JSON ObjectNode trees incrementally from version N to N+1.
 */
public final class MigrationChain {
    private final String formatName;
    private final int currentVersion;
    private final Map<Integer, MigrationStep> migrations = new HashMap<>();

    public MigrationChain(String formatName, int currentVersion) {
        this.formatName = formatName;
        this.currentVersion = currentVersion;
    }

    public void register(int fromVersion, MigrationStep step) {
        migrations.put(fromVersion, step);
    }

    public ObjectNode migrate(ObjectNode root) {
        if (!root.has("version")) {
            throw new IllegalArgumentException("File format missing required 'version' field for " + formatName);
        }

        int version = root.get("version").asInt();
        if (version > currentVersion) {
            Log.error(LogCategory.DATA, "Unknown newer %s version %d (max supported is %d)",
                formatName, version, currentVersion);
            throw new IllegalArgumentException(String.format("Unknown newer %s version: %d (current is %d)",
                formatName, version, currentVersion));
        }

        while (version < currentVersion) {
            MigrationStep step = migrations.get(version);
            if (step == null) {
                throw new IllegalStateException(String.format("Missing migration step from version %d to %d for %s",
                    version, version + 1, formatName));
            }

            Log.info(LogCategory.DATA, "Migrating %s from version %d to %d", formatName, version, version + 1);
            step.migrate(root);
            version++;
            root.put("version", version);
        }

        return root;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }
}
