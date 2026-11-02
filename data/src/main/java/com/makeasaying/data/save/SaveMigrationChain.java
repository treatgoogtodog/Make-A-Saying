package com.makeasaying.data.save;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Version migration pipeline for checkpoint save files per M7b brief.
 */
public final class SaveMigrationChain {
    private final Map<Integer, Consumer<ObjectNode>> migrations = new HashMap<>();

    public void register(int fromVersion, Consumer<ObjectNode> migration) {
        migrations.put(fromVersion, migration);
    }

    public int migrate(ObjectNode root, int targetVersion) {
        if (!root.has("version")) {
            throw new IllegalArgumentException("SAVE_ERR_MISSING_VERSION: Save file root missing version field");
        }
        int currentVersion = root.get("version").asInt();
        if (currentVersion > targetVersion) {
            throw new IllegalArgumentException(String.format("SAVE_ERR_UNKNOWN_NEWER_VERSION: Save version %d is newer than supported version %d", currentVersion, targetVersion));
        }

        while (currentVersion < targetVersion) {
            Consumer<ObjectNode> step = migrations.get(currentVersion);
            if (step == null) {
                throw new IllegalStateException(String.format("SAVE_ERR_MISSING_MIGRATION: No migration step from version %d to %d", currentVersion, currentVersion + 1));
            }
            Log.info(LogCategory.DATA, "Migrating save data from version %d to %d", currentVersion, currentVersion + 1);
            step.accept(root);
            currentVersion++;
            root.put("version", currentVersion);
        }

        return currentVersion;
    }
}
