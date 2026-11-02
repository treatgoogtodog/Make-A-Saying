package com.makeasaying.engine.world;

import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Room transition state machine per AGENTS.md section 7.7 and M5b brief.
 * Owns transition states, exit overlap detection, arrival re-arming,
 * and deterministic tick suspension while waiting for worker preload.
 */
public final class RoomTransitionManager {
    public static final long TIMEOUT_NANOS = 10_000_000_000L; // 10 seconds monotonic timeout

    @FunctionalInterface
    public interface SwapCallback {
        void performSwap(RoomExit exit);
    }

    private final RoomTransitionConfig config;
    private final java.util.function.LongSupplier nanoTimeSupplier;
    private RoomTransitionState state = RoomTransitionState.IDLE;
    private int transitionTick = 0;
    private long waitStartTimeNanos = -1L;
    private boolean waitingForLoad = false;
    private RoomExit pendingExit = null;
    private Runnable pendingCustomSwap = null;

    private static final int MAX_DISABLED_EXITS = 16;
    private final String[] disabledExitIds = new String[MAX_DISABLED_EXITS];
    private int disabledExitCount = 0;

    public RoomTransitionManager(RoomTransitionConfig config, java.util.function.LongSupplier nanoTimeSupplier) {
        this.config = config != null ? config : RoomTransitionConfig.DEFAULT;
        this.nanoTimeSupplier = nanoTimeSupplier != null ? nanoTimeSupplier : System::nanoTime;
    }

    public RoomTransitionManager(RoomTransitionConfig config) {
        this(config, System::nanoTime);
    }

    public RoomTransitionManager() {
        this(RoomTransitionConfig.DEFAULT, System::nanoTime);
    }

    public RoomTransitionConfig getConfig() {
        return config;
    }

    public RoomTransitionState getState() {
        return state;
    }

    public int getTransitionTick() {
        return transitionTick;
    }

    public boolean isWaitingForLoad() {
        return waitingForLoad;
    }

    public boolean isEntitySystemsFrozen() {
        return state != RoomTransitionState.IDLE;
    }

    public RoomExit getPendingExit() {
        return pendingExit;
    }

    public Set<String> getDisabledExitIds() {
        Set<String> set = new HashSet<>();
        for (int i = 0; i < disabledExitCount; i++) {
            set.add(disabledExitIds[i]);
        }
        return Collections.unmodifiableSet(set);
    }

    public void disableExit(String exitId) {
        if (exitId == null) return;
        for (int i = 0; i < disabledExitCount; i++) {
            if (exitId.equals(disabledExitIds[i])) return;
        }
        if (disabledExitCount < MAX_DISABLED_EXITS) {
            disabledExitIds[disabledExitCount++] = exitId;
        }
    }

    public boolean isExitDisabled(String exitId) {
        if (exitId == null) return false;
        for (int i = 0; i < disabledExitCount; i++) {
            if (exitId.equals(disabledExitIds[i])) return true;
        }
        return false;
    }

    public boolean isTransitionActive() {
        return state != RoomTransitionState.IDLE;
    }

    public void startTransition(RoomExit exit) {
        if (exit == null || state != RoomTransitionState.IDLE) return;
        this.pendingExit = exit;
        this.pendingCustomSwap = null;
        this.state = RoomTransitionState.FADE_OUT;
        this.transitionTick = 0;
        this.waitStartTimeNanos = -1L;
        this.waitingForLoad = false;
    }

    public void startCustomTransition(Runnable onSwap) {
        if (onSwap == null || state != RoomTransitionState.IDLE) return;
        this.pendingExit = null;
        this.pendingCustomSwap = onSwap;
        this.state = RoomTransitionState.FADE_OUT;
        this.transitionTick = 0;
        this.waitStartTimeNanos = -1L;
        this.waitingForLoad = false;
    }

    public void abortTransition(String reason) {
        Log.error(LogCategory.ENGINE, "Transition aborted: %s", reason);
        if (pendingExit != null) {
            disableExit(pendingExit.id());
        }
        this.pendingCustomSwap = null;
        this.state = RoomTransitionState.FADE_IN;
        this.transitionTick = 0;
        this.waitingForLoad = false;
    }

    /**
     * Updates transition state. Called once per simulation tick.
     * When waiting for an async load in HOLD_WAIT_SWAP, returns false to signal
     * that tick advance should be suspended (accumulator discarded, black rendered).
     * Returns true when tick advance proceeds normally.
     */
    public boolean update(
        long controlledEntityId,
        World world,
        Room currentRoom,
        BooleanSupplier isTargetReady,
        SwapCallback swapCallback
    ) {
        return update(controlledEntityId, world, currentRoom, null, isTargetReady, swapCallback);
    }

    /**
     * Updates transition state with FlagStore condition evaluation and room_temp clearing.
     */
    public boolean update(
        long controlledEntityId,
        World world,
        Room currentRoom,
        com.makeasaying.engine.flags.FlagStore flagStore,
        BooleanSupplier isTargetReady,
        SwapCallback swapCallback
    ) {
        if (state == RoomTransitionState.IDLE) {
            updateExitOverlapsAndTrigger(controlledEntityId, world, currentRoom, flagStore);
            return true;
        } else if (state == RoomTransitionState.FADE_OUT) {
            transitionTick++;
            if (transitionTick >= config.fadeOutTicks()) {
                state = RoomTransitionState.HOLD_WAIT_SWAP;
                transitionTick = 0;
                waitStartTimeNanos = -1L;
            }
            return true;
        } else if (state == RoomTransitionState.HOLD_WAIT_SWAP) {
            if (transitionTick < config.holdTicks()) {
                transitionTick++;
                return true;
            }

            // We have held for holdTicks. Check if target room is ready to swap.
            boolean ready = (pendingCustomSwap != null) || ((isTargetReady != null) && isTargetReady.getAsBoolean());
            if (!ready) {
                long now = nanoTimeSupplier.getAsLong();
                if (waitStartTimeNanos < 0) {
                    waitStartTimeNanos = now;
                }
                waitingForLoad = true;
                if (now - waitStartTimeNanos > TIMEOUT_NANOS) {
                    abortTransition("Target room load timed out after 10 seconds");
                    return true;
                }
                // Suspend tick advance
                return false;
            }

            // Target room is ready: perform swap!
            waitingForLoad = false;
            waitStartTimeNanos = -1L;
            if (flagStore != null) {
                flagStore.clearRoomTemp(transitionTick);
            }
            if (pendingCustomSwap != null) {
                Runnable customSwap = pendingCustomSwap;
                pendingCustomSwap = null;
                customSwap.run();
            } else if (swapCallback != null && pendingExit != null) {
                swapCallback.performSwap(pendingExit);
            }

            state = RoomTransitionState.FADE_IN;
            transitionTick = 0;
            return true;
        } else if (state == RoomTransitionState.FADE_IN) {
            transitionTick++;
            if (transitionTick >= config.fadeInTicks()) {
                state = RoomTransitionState.IDLE;
                transitionTick = 0;
                pendingExit = null;
            }
            return true;
        }
        return true;
    }

    private void updateExitOverlapsAndTrigger(
        long controlledEntityId,
        World world,
        Room currentRoom,
        com.makeasaying.engine.flags.FlagStore flagStore
    ) {
        if (currentRoom == null || world == null || !world.isAlive(controlledEntityId)) return;

        BodyComponent body = world.getBody(controlledEntityId);
        ColliderComponent collider = world.getCollider(controlledEntityId);
        if (body == null || collider == null) return;

        int px = body.x + collider.offsetX;
        int py = body.y + collider.offsetY;
        int pw = collider.width;
        int ph = collider.height;

        java.util.List<RoomExit> exits = currentRoom.exits();
        int exitCount = exits.size();

        // Check if player left any disabled exit zones to re-arm them (zero allocation backwards scan)
        for (int i = disabledExitCount - 1; i >= 0; i--) {
            String disabledId = disabledExitIds[i];
            boolean stillOverlaps = false;
            for (int j = 0; j < exitCount; j++) {
                RoomExit exit = exits.get(j);
                if (exit.id().equals(disabledId)) {
                    if (exit.overlaps(px, py, pw, ph)) {
                        stillOverlaps = true;
                    }
                    break;
                }
            }
            if (!stillOverlaps) {
                // Re-armed: remove by swapping with end element
                disabledExitIds[i] = disabledExitIds[disabledExitCount - 1];
                disabledExitIds[--disabledExitCount] = null;
            }
        }

        // Check if player stepped into an active (non-disabled) exit (zero allocation)
        for (int j = 0; j < exitCount; j++) {
            RoomExit exit = exits.get(j);
            if (exit.overlaps(px, py, pw, ph)) {
                if (!isExitDisabled(exit.id())) {
                    if (exit.condition() != null && flagStore != null && !exit.condition().evaluate(flagStore, currentRoom.id())) {
                        com.makeasaying.engine.util.Log.debug(com.makeasaying.engine.util.LogCategory.ENGINE,
                            "Exit '%s' condition false, transition blocked", exit.id());
                    } else {
                        startTransition(exit);
                        break;
                    }
                }
            }
        }
    }

    public float getFadeAlpha() {
        RoomTransitionState s = this.state;
        if (s == RoomTransitionState.FADE_OUT) {
            return Math.min(1.0f, (float) transitionTick / Math.max(1, config.fadeOutTicks()));
        } else if (s == RoomTransitionState.HOLD_WAIT_SWAP) {
            return 1.0f;
        } else if (s == RoomTransitionState.FADE_IN) {
            return Math.max(0.0f, 1.0f - ((float) transitionTick / Math.max(1, config.fadeInTicks())));
        }
        return 0.0f;
    }
}
