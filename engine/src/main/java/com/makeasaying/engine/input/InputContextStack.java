package com.makeasaying.engine.input;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Context stack (e.g. GAMEPLAY, MENU, DIALOG, EDITOR) per AGENTS.md section 7.4.
 * Only the top context receives active input.
 * On change, clears transient pressed/released flags and swallows currently held physical keys.
 */
public final class InputContextStack {
    private final Deque<String> stack = new ArrayDeque<>();
    private Consumer<String> onContextChangeListener;

    public InputContextStack(String defaultContext) {
        push(defaultContext);
    }

    public InputContextStack() {
        this(InputContext.GAMEPLAY);
    }

    public void setOnContextChangeListener(Consumer<String> listener) {
        this.onContextChangeListener = listener;
    }

    public synchronized void push(String context) {
        if (context == null || context.isBlank()) {
            throw new IllegalArgumentException("Context cannot be null or empty");
        }
        stack.push(context);
        notifyChange();
    }

    public synchronized String pop() {
        if (stack.size() <= 1) {
            // Root context cannot be popped
            return stack.peek();
        }
        String popped = stack.pop();
        notifyChange();
        return popped;
    }

    public synchronized String getActiveContext() {
        return stack.isEmpty() ? InputContext.GAMEPLAY : stack.peek();
    }

    public synchronized int size() {
        return stack.size();
    }

    private void notifyChange() {
        if (onContextChangeListener != null && !stack.isEmpty()) {
            onContextChangeListener.accept(stack.peek());
        }
    }
}
