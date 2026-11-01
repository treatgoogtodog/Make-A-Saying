package com.makeasaying.engine.entity;

import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.ControllerComponent;
import com.makeasaying.engine.entity.components.FacingComponent;
import com.makeasaying.engine.entity.components.JumpStateComponent;
import com.makeasaying.engine.entity.components.RoomScopeComponent;

/**
 * Shared factory for instantiating the player entity with standard components
 * per AGENTS.md section 7.2.
 * Used by both Demo scenes and Editor Play mode.
 */
public final class PlayerFactory {
    private PlayerFactory() {}

    public static long createPlayer(World world, int x, int y) {
        if (world == null) throw new IllegalArgumentException("world must not be null");

        long playerId = world.createEntity();
        world.setBody(playerId, new BodyComponent(x, y));
        world.setCollider(playerId, new ColliderComponent(12, 36));
        world.setFacing(playerId, new FacingComponent(1));
        world.setController(playerId, new ControllerComponent(ControlSource.PLAYER));
        world.setJumpState(playerId, new JumpStateComponent());
        world.setRoomScope(playerId, new RoomScopeComponent(RoomScopeComponent.Scope.PERSISTENT_CARRIED));
        world.applyDeferredLifecycle();
        return playerId;
    }
}
