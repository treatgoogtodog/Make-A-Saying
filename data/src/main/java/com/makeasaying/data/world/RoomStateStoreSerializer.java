package com.makeasaying.data.world;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.makeasaying.engine.world.RoomStateStore;

import java.util.Iterator;
import java.util.Map;

/**
 * Serializes and deserializes RoomStateStore to/from Jackson trees per M5b brief.
 * Lives in data module.
 */
public final class RoomStateStoreSerializer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RoomStateStoreSerializer() {}

    public static ObjectNode toTree(RoomStateStore store) {
        ObjectNode root = MAPPER.createObjectNode();
        for (RoomStateStore.Entry entry : store.getAllEntriesSorted()) {
            ObjectNode roomNode = (ObjectNode) root.get(entry.roomId());
            if (roomNode == null) {
                roomNode = MAPPER.createObjectNode();
                root.set(entry.roomId(), roomNode);
            }

            if (entry.type() == RoomStateStore.ValueType.INT) {
                roomNode.put(entry.key(), entry.intValue());
            } else {
                roomNode.put(entry.key(), entry.stringValue());
            }
        }
        return root;
    }

    public static void fromTree(JsonNode root, RoomStateStore store) {
        if (root == null || !root.isObject()) return;

        Iterator<Map.Entry<String, JsonNode>> roomFields = root.fields();
        while (roomFields.hasNext()) {
            Map.Entry<String, JsonNode> roomEntry = roomFields.next();
            String roomId = roomEntry.getKey();
            JsonNode roomNode = roomEntry.getValue();
            if (!roomNode.isObject()) continue;

            Iterator<Map.Entry<String, JsonNode>> propFields = roomNode.fields();
            while (propFields.hasNext()) {
                Map.Entry<String, JsonNode> propEntry = propFields.next();
                String key = propEntry.getKey();
                JsonNode valNode = propEntry.getValue();

                if (valNode.isInt() || valNode.isLong()) {
                    store.setInt(roomId, key, valNode.asInt());
                } else if (valNode.isTextual()) {
                    store.setString(roomId, key, valNode.asText());
                }
            }
        }
    }
}
