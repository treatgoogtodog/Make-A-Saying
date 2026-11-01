package com.makeasaying.engine.input;

import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

/**
 * Preallocated ring buffer of raw key events per AGENTS.md section 7.4.
 * Thread-safe for render thread enqueuing and simulation tick draining on main thread.
 * Zero per-event allocation.
 */
public final class RawInputQueue {
    public static final int DEFAULT_CAPACITY = 256;

    private final int[] keyCodes;
    private final boolean[] isDown;
    private final int capacity;

    private int head = 0;
    private int tail = 0;
    private int count = 0;

    public RawInputQueue(int capacity) {
        this.capacity = Math.max(32, capacity);
        this.keyCodes = new int[this.capacity];
        this.isDown = new boolean[this.capacity];
    }

    public RawInputQueue() {
        this(DEFAULT_CAPACITY);
    }

    public synchronized boolean enqueue(int keyCode, boolean down) {
        if (count >= capacity) {
            Log.error(LogCategory.INPUT,
                "Raw input queue overflow! Dropping event: key=%s, down=%b",
                KeyNames.toKeyName(keyCode), down);
            return false;
        }

        keyCodes[tail] = keyCode;
        isDown[tail] = down;
        tail = (tail + 1) % capacity;
        count++;
        return true;
    }

    public synchronized int size() {
        return count;
    }

    public synchronized boolean isEmpty() {
        return count == 0;
    }

    public synchronized int peekKeyCode() {
        if (count == 0) throw new IllegalStateException("Queue is empty");
        return keyCodes[head];
    }

    public synchronized boolean peekDown() {
        if (count == 0) throw new IllegalStateException("Queue is empty");
        return isDown[head];
    }

    public synchronized void dequeue() {
        if (count == 0) throw new IllegalStateException("Queue is empty");
        head = (head + 1) % capacity;
        count--;
    }

    public synchronized void clear() {
        head = 0;
        tail = 0;
        count = 0;
    }
}
