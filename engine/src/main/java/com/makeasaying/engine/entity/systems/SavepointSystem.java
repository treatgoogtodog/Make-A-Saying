package com.makeasaying.engine.entity.systems;

import com.makeasaying.engine.entity.ControlSource;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.ControllerComponent;
import com.makeasaying.engine.entity.components.InputStateComponent;
import com.makeasaying.engine.entity.components.SavepointComponent;
import com.makeasaying.engine.input.Actions;
import com.makeasaying.engine.input.InputState;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

/**
 * Handles savepoint interaction in Step 8 ("Triggers and Zones") per M7b brief.
 * When the player collider overlaps an active savepoint trigger and presses interact,
 * consumes the press and dispatches a save request if permitted. Zero per-tick allocation.
 */
public final class SavepointSystem {

    @FunctionalInterface
    public interface SaveTriggerCallback {
        void onSaveRequested(String savepointInstanceId, int savepointX, int savepointY);
    }

    /**
     * Checks if the player is currently overlapping an active savepoint collider.
     * Zero-allocation query used by both prompt display and interaction processing.
     */
    public int getOverlappingSavepointIndex(World world) {
        int playerIdx = -1;
        for (int i = 1; i < World.MAX_ENTITIES; i++) {
            if (!world.isAliveIndex(i)) continue;
            ControllerComponent ctrl = world.getControllerByIndex(i);
            if (ctrl != null && ctrl.source == ControlSource.PLAYER) {
                playerIdx = i;
                break;
            }
        }
        if (playerIdx == -1) return -1;

        BodyComponent playerBody = world.getBodyByIndex(playerIdx);
        ColliderComponent playerCol = world.getColliderByIndex(playerIdx);
        if (playerBody == null || playerCol == null) return -1;

        int px = playerBody.x + playerCol.offsetX;
        int py = playerBody.y + playerCol.offsetY;
        int pw = playerCol.width;
        int ph = playerCol.height;

        for (int i = 1; i < World.MAX_ENTITIES; i++) {
            if (!world.isAliveIndex(i)) continue;
            SavepointComponent sp = world.getSavepointByIndex(i);
            if (sp == null || !sp.active) continue;

            BodyComponent spBody = world.getBodyByIndex(i);
            ColliderComponent spCol = world.getColliderByIndex(i);
            if (spBody == null || spCol == null) continue;

            int sx = spBody.x + spCol.offsetX;
            int sy = spBody.y + spCol.offsetY;
            int sw = spCol.width;
            int sh = spCol.height;

            if (px < sx + sw && px + pw > sx && py < sy + sh && py + ph > sy) {
                return i;
            }
        }
        return -1;
    }

    public boolean isPlayerOverlappingSavepoint(World world) {
        return getOverlappingSavepointIndex(world) != -1;
    }

    public void update(
        World world,
        long currentTick,
        boolean isEditorPlay,
        boolean isTransitionOrLoadInFlight,
        SaveTriggerCallback callback
    ) {
        int spIdx = getOverlappingSavepointIndex(world);
        if (spIdx == -1) return;

        // Player is overlapping an active savepoint
        int playerIdx = -1;
        for (int i = 1; i < World.MAX_ENTITIES; i++) {
            if (!world.isAliveIndex(i)) continue;
            ControllerComponent ctrl = world.getControllerByIndex(i);
            if (ctrl != null && ctrl.source == ControlSource.PLAYER) {
                playerIdx = i;
                break;
            }
        }
        if (playerIdx == -1) return;

        InputStateComponent inputComp = world.getInputStateByIndex(playerIdx);
        if (inputComp == null || inputComp.state == null) return;
        InputState input = inputComp.state;

        if (!input.isPressed(Actions.INTERACT)) return;

        // Consume the press per M2 rule
        input.consumePress(Actions.INTERACT);

        if (isEditorPlay) {
            Log.warn(LogCategory.ENGINE, "Saving is disabled in Editor Play mode");
            return;
        }
        if (isTransitionOrLoadInFlight) {
            Log.warn(LogCategory.ENGINE, "Cannot save while a transition or load is in flight");
            return;
        }

        BodyComponent spBody = world.getBodyByIndex(spIdx);
        String spInstanceId = world.getInstanceId(world.getIdByIndex(spIdx));
        if (callback != null && spInstanceId != null && spBody != null) {
            callback.onSaveRequested(spInstanceId, spBody.x, spBody.y);
        }
    }
}
