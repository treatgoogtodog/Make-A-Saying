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
    private int extra;

    public EntityEvent() {}

    public EntityEvent(EntityEventType type, long entityId, int detail) {
        this(type, entityId, 0L, detail, 0);
    }

    public EntityEvent(EntityEventType type, long entityId, long targetId, int detail) {
        this(type, entityId, targetId, detail, 0);
    }

    public EntityEvent(EntityEventType type, long entityId, long targetId, int detail, int extra) {
        set(type, entityId, targetId, detail, extra);
    }

    public void set(EntityEventType type, long entityId, int detail) {
        set(type, entityId, 0L, detail, 0);
    }

    public void set(EntityEventType type, long entityId, long targetId, int detail) {
        set(type, entityId, targetId, detail, 0);
    }

    public void set(EntityEventType type, long entityId, long targetId, int detail, int extra) {
        this.type = type;
        this.entityId = entityId;
        this.targetId = targetId;
        this.detail = detail;
        this.extra = extra;
    }

    public void setFlagChanged(int flagIndex, int oldValue, int newValue) {
        this.type = EntityEventType.FLAG_CHANGED;
        this.entityId = flagIndex;
        this.targetId = 0L;
        this.detail = oldValue;
        this.extra = newValue;
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

    public int extra() {
        return extra;
    }

    public int flagIndex() {
        return (int) entityId;
    }

    public int flagOldValue() {
        return detail;
    }

    public int flagNewValue() {
        return extra;
    }
}
