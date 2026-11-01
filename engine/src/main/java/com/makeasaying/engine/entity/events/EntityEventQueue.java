package com.makeasaying.engine.entity.events;

import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Deterministic preallocated event queue with cascade limits per AGENTS.md section 7.2.
 * Preallocates event objects to guarantee zero allocation during ticks.
 */
public final class EntityEventQueue {
    public static final int DEFAULT_CAPACITY = 256;
    public static final int CASCADE_LIMIT = 500;

    private final EntityEvent[] ringBuffer;
    private final int capacity;
    private int head = 0;
    private int tail = 0;
    private int count = 0;

    private final List<Consumer<EntityEvent>> listeners = new ArrayList<>();

    public EntityEventQueue(int capacity) {
        this.capacity = Math.max(32, capacity);
        this.ringBuffer = new EntityEvent[this.capacity];
        for (int i = 0; i < this.capacity; i++) {
            this.ringBuffer[i] = new EntityEvent();
        }
    }

    public EntityEventQueue() {
        this(DEFAULT_CAPACITY);
    }

    public synchronized void subscribe(Consumer<EntityEvent> listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void unsubscribe(Consumer<EntityEvent> listener) {
        listeners.remove(listener);
    }

    public synchronized void emit(EntityEventType type, long entityId, int detail) {
        if (count >= capacity) {
            Log.error(LogCategory.ENTITY, "Event queue overflow! Dropping event %s for entity %d",
                type, entityId);
            return;
        }

        ringBuffer[tail].set(type, entityId, detail);
        tail = (tail + 1) % capacity;
        count++;
    }

    public synchronized void dispatch() {
        int cascadeCounter = 0;

        while (count > 0) {
            if (++cascadeCounter > CASCADE_LIMIT) {
                Log.error(LogCategory.ENTITY,
                    "Event cascade limit exceeded (%d events processed in single tick)! Halting dispatch to prevent infinite loop.",
                    CASCADE_LIMIT);
                clear();
                break;
            }

            EntityEvent event = ringBuffer[head];
            head = (head + 1) % capacity;
            count--;

            for (int i = 0; i < listeners.size(); i++) {
                Consumer<EntityEvent> listener = listeners.get(i);
                try {
                    listener.accept(event);
                } catch (Throwable t) {
                    Log.error(LogCategory.ENTITY, "Error in event listener for %s: %s", t, event.type(), t.getMessage());
                }
            }
        }
    }

    public synchronized int size() {
        return count;
    }

    public synchronized void clear() {
        head = 0;
        tail = 0;
        count = 0;
    }
}
