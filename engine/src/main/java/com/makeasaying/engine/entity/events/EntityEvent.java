package com.makeasaying.engine.entity.events;

/**
 * Event emitted by simulation systems and queued deterministically per AGENTS.md section 7.2.
 * Mutable instance reused in preallocated ring buffer to ensure zero per-tick allocations.
 */
public final class EntityEvent {
    private EntityEventType type;
    private long entityId;
    private long targetId;
    private int detail;

    public EntityEvent() {}

    public EntityEvent(EntityEventType type, long entityId, int detail) {
        this(type, entityId, 0L, detail);
    }

    public EntityEvent(EntityEventType type, long entityId, long targetId, int detail) {
        set(type, entityId, targetId, detail);
    }

    public void set(EntityEventType type, long entityId, int detail) {
        set(type, entityId, 0L, detail);
    }

    public void set(EntityEventType type, long entityId, long targetId, int detail) {
        this.type = type;
        this.entityId = entityId;
        this.targetId = targetId;
        this.detail = detail;
    }

    public EntityEventType type() {
        return type;
    }

    public long entityId() {
        return entityId;
    }

    public long targetId() {
        return targetId;
    }

    public int detail() {
        return detail;
    }
}
