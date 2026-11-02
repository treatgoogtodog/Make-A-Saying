package com.makeasaying.engine.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of active input actions and digital axes per tick.
 * Holds no references to libGDX entities. Completely allocation-free in ticks.
 */
public final class InputState {
    private final ActionRegistry registry;
    private final Map<String, Integer> actionIndices = new HashMap<>();
    private final List<String> indexToAction = new ArrayList<>();

    private final boolean[] down;
    private final boolean[] pressed;
    private final boolean[] released;
    private final long[] lastPressedOrder;
    private final int[] axisValues;

    private record AxisBinding(int axisIndex, int negativeIndex, int positiveIndex) {}
    private final AxisBinding[] axisBindings;

    private long globalPressCounter = 0L;

    public InputState(ActionRegistry registry) {
        this.registry = registry;
        int idx = 0;
        for (ActionDefinition def : registry.getAll()) {
            actionIndices.put(def.id(), idx);
            indexToAction.add(def.id());
            idx++;
        }

        int size = actionIndices.size();
        this.down = new boolean[size];
        this.pressed = new boolean[size];
        this.released = new boolean[size];
        this.lastPressedOrder = new long[size];
        this.axisValues = new int[size];

        List<AxisBinding> bindingsList = new ArrayList<>();
        for (ActionDefinition def : registry.getAll()) {
            if (def.type() == ActionDefinition.ActionType.AXIS) {
                Integer axisIdx = actionIndices.get(def.id());
                Integer negIdx = actionIndices.get(def.negativeButton());
                Integer posIdx = actionIndices.get(def.positiveButton());
                if (axisIdx != null && negIdx != null && posIdx != null) {
                    bindingsList.add(new AxisBinding(axisIdx, negIdx, posIdx));
                }
            }
        }
        this.axisBindings = bindingsList.toArray(new AxisBinding[0]);
    }

    public void beginTick() {
        // Clear single-tick pressed and released flags
        for (int i = 0; i < pressed.length; i++) {
            pressed[i] = false;
            released[i] = false;
        }
    }

    public void setButtonDown(String actionId, boolean isDown) {
        Integer idx = actionIndices.get(actionId);
        if (idx == null) return;

        boolean wasDown = down[idx];
        if (isDown && !wasDown) {
            down[idx] = true;
            pressed[idx] = true;
            lastPressedOrder[idx] = ++globalPressCounter;
        } else if (!isDown && wasDown) {
            down[idx] = false;
            released[idx] = true;
        }
    }

    public void resolveAxes() {
        for (int i = 0; i < axisBindings.length; i++) {
            AxisBinding ab = axisBindings[i];
            boolean negDown = down[ab.negativeIndex];
            boolean posDown = down[ab.positiveIndex];

            if (negDown && posDown) {
                // Last-pressed-wins per DEC-004
                axisValues[ab.axisIndex] = (lastPressedOrder[ab.positiveIndex] > lastPressedOrder[ab.negativeIndex]) ? 1 : -1;
            } else if (posDown) {
                axisValues[ab.axisIndex] = 1;
            } else if (negDown) {
                axisValues[ab.axisIndex] = -1;
            } else {
                axisValues[ab.axisIndex] = 0;
            }
        }
    }

    public boolean isDown(String actionId) {
        Integer idx = actionIndices.get(actionId);
        return idx != null && down[idx];
    }

    public boolean isPressed(String actionId) {
        Integer idx = actionIndices.get(actionId);
        return idx != null && pressed[idx];
    }

    public void consumePress(String actionId) {
        Integer idx = actionIndices.get(actionId);
        if (idx != null) {
            pressed[idx] = false;
        }
    }

    public boolean isReleased(String actionId) {
        Integer idx = actionIndices.get(actionId);
        return idx != null && released[idx];
    }

    public int getAxis(String axisId) {
        Integer idx = actionIndices.get(axisId);
        return idx != null ? axisValues[idx] : 0;
    }

    public List<String> getHeldActionIds() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < down.length; i++) {
            if (down[i]) {
                list.add(indexToAction.get(i));
            }
        }
        return list;
    }

    public void clearTransientFlags() {
        for (int i = 0; i < pressed.length; i++) {
            pressed[i] = false;
            released[i] = false;
        }
    }

    public void clearAll() {
        for (int i = 0; i < down.length; i++) {
            down[i] = false;
            pressed[i] = false;
            released[i] = false;
            axisValues[i] = 0;
            lastPressedOrder[i] = 0L;
        }
        globalPressCounter = 0L;
    }

    public void copyFrom(InputState source) {
        if (source == null) return;
        System.arraycopy(source.down, 0, this.down, 0, this.down.length);
        System.arraycopy(source.pressed, 0, this.pressed, 0, this.pressed.length);
        System.arraycopy(source.released, 0, this.released, 0, this.released.length);
        System.arraycopy(source.axisValues, 0, this.axisValues, 0, this.axisValues.length);
        System.arraycopy(source.lastPressedOrder, 0, this.lastPressedOrder, 0, this.lastPressedOrder.length);
        this.globalPressCounter = source.globalPressCounter;
    }

    public InputState copy() {
        InputState result = new InputState(this.registry);
        result.copyFrom(this);
        return result;
    }
}
