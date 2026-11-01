package com.makeasaying.engine.entity.components;

/**
 * Component determining whether an entity is carried across room transitions
 * or destroyed when leaving the room per M5b brief.
 */
public final class RoomScopeComponent {
    public enum Scope {
        PERSISTENT_CARRIED,
        ROOM_LOCAL
    }

    public Scope scope;

    public RoomScopeComponent(Scope scope) {
        this.scope = scope != null ? scope : Scope.ROOM_LOCAL;
    }

    public RoomScopeComponent() {
        this(Scope.ROOM_LOCAL);
    }

    public boolean isCarried() {
        return scope == Scope.PERSISTENT_CARRIED;
    }
}
