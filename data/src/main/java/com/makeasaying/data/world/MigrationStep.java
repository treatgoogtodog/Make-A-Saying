package com.makeasaying.data.world;

import com.fasterxml.jackson.databind.node.ObjectNode;

@FunctionalInterface
public interface MigrationStep {
    void migrate(ObjectNode root);
}
