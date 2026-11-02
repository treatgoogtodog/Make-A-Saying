package com.makeasaying.engine.entity;

import com.makeasaying.engine.schema.ComponentSchemaRegistry;

import java.util.List;
import java.util.Map;

/**
 * Factory for creating the player entity as a thin wrapper over EntitySpawner per M6b brief.
 * Collider size and initial state come from the player EntityDefinition rather than hardcoded values.
 */
public final class PlayerFactory {
    public static final EntityDefinition DEFAULT_PLAYER_DEF = new EntityDefinition(
        1,
        "player",
        List.of("player"),
        "#00FF00",
        false,
        Map.of(
            "body", Map.of("x", 0, "y", 0),
            "collider", Map.of("width", 12, "height", 36, "offsetX", 0, "offsetY", 0),
            "facing", Map.of("facingRight", true),
            "controller", Map.of("source", "PLAYER"),
            "room_scope", Map.of("scope", "PERSISTENT_CARRIED"),
            "jump_state", Map.of("coyoteTicks", 0, "jumpBufferTicks", 0)
        )
    );

    private static final EntitySpawner DEFAULT_SPAWNER = new EntitySpawner(ComponentSchemaRegistry.createDefault());

    private PlayerFactory() {}

    public static long createPlayer(EntitySpawner spawner, EntityDefinition playerDef, World world, int x, int y) {
        if (world == null) throw new IllegalArgumentException("world must not be null");
        EntitySpawner s = spawner != null ? spawner : DEFAULT_SPAWNER;
        EntityDefinition def = playerDef != null ? playerDef : DEFAULT_PLAYER_DEF;

        long playerId = s.spawn(world, def, "player", x, y, null);
        world.applyDeferredLifecycle();
        return playerId;
    }

    public static long createPlayer(World world, int x, int y) {
        return createPlayer(DEFAULT_SPAWNER, DEFAULT_PLAYER_DEF, world, x, y);
    }
}
