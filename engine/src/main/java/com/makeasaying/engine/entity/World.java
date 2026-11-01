package com.makeasaying.engine.entity;

import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.ControllerComponent;
import com.makeasaying.engine.entity.components.FacingComponent;
import com.makeasaying.engine.entity.components.InputStateComponent;
import com.makeasaying.engine.entity.components.JumpStateComponent;
import com.makeasaying.engine.entity.events.EntityEventQueue;

/**
 * World storage and lifecycle manager for entities per DEC-006.
 * Zero-allocation during ticks; ascending index order iteration for determinism.
 */
public final class World {
    public static final int MAX_ENTITIES = 256;

    private final int[] generations = new int[MAX_ENTITIES];
    private final boolean[] alive = new boolean[MAX_ENTITIES];

    // Preallocated dense component stores
    private final BodyComponent[] bodies = new BodyComponent[MAX_ENTITIES];
    private final ColliderComponent[] colliders = new ColliderComponent[MAX_ENTITIES];
    private final FacingComponent[] facings = new FacingComponent[MAX_ENTITIES];
    private final ControllerComponent[] controllers = new ControllerComponent[MAX_ENTITIES];
    private final InputStateComponent[] inputStates = new InputStateComponent[MAX_ENTITIES];
    private final JumpStateComponent[] jumpStates = new JumpStateComponent[MAX_ENTITIES];
    private final com.makeasaying.engine.entity.components.PlatformComponent[] platforms = new com.makeasaying.engine.entity.components.PlatformComponent[MAX_ENTITIES];

    private final EntityEventQueue eventQueue;
    private final int[] freeIndices = new int[MAX_ENTITIES];
    private int freeCount = 0;

    // Deferred lifecycle primitive arrays (strictly zero allocation)
    private final long[] deferredSpawns = new long[MAX_ENTITIES];
    private int deferredSpawnsCount = 0;
    private final long[] deferredDespawns = new long[MAX_ENTITIES];
    private int deferredDespawnsCount = 0;

    public World(EntityEventQueue eventQueue) {
        this.eventQueue = eventQueue != null ? eventQueue : new EntityEventQueue();
        reset();
    }

    public World() {
        this(new EntityEventQueue());
    }

    public void reset() {
        eventQueue.clear();
        deferredSpawnsCount = 0;
        deferredDespawnsCount = 0;
        freeCount = 0;

        for (int i = 0; i < MAX_ENTITIES; i++) {
            alive[i] = false;
            generations[i] = 1;
            bodies[i] = null;
            colliders[i] = null;
            facings[i] = null;
            controllers[i] = null;
            inputStates[i] = null;
            jumpStates[i] = null;
            platforms[i] = null;
        }

        // Push free indices in reverse order so lowest index (1) is popped first
        for (int i = MAX_ENTITIES - 1; i >= 1; i--) {
            freeIndices[freeCount++] = i;
        }
    }

    public long createEntity() {
        if (freeCount == 0) {
            throw new IllegalStateException("Entity limit reached (" + MAX_ENTITIES + ")");
        }
        int index = freeIndices[--freeCount];
        int gen = generations[index];
        long id = EntityId.of(index, gen);

        alive[index] = true;
        if (deferredSpawnsCount < MAX_ENTITIES) {
            deferredSpawns[deferredSpawnsCount++] = id;
        }
        return id;
    }

    public void destroyEntity(long id) {
        if (isAlive(id)) {
            if (deferredDespawnsCount < MAX_ENTITIES) {
                deferredDespawns[deferredDespawnsCount++] = id;
            }
        }
    }

    public boolean isAlive(long id) {
        int index = EntityId.getIndex(id);
        if (index <= 0 || index >= MAX_ENTITIES) return false;
        return alive[index] && generations[index] == EntityId.getGeneration(id);
    }

    public void applyDeferredLifecycle() {
        deferredSpawnsCount = 0;

        for (int i = 0; i < deferredDespawnsCount; i++) {
            long id = deferredDespawns[i];
            int index = EntityId.getIndex(id);
            if (index > 0 && index < MAX_ENTITIES && alive[index] && generations[index] == EntityId.getGeneration(id)) {
                alive[index] = false;
                generations[index]++; // Increment generation on recycled slot
                bodies[index] = null;
                colliders[index] = null;
                facings[index] = null;
                controllers[index] = null;
                inputStates[index] = null;
                jumpStates[index] = null;
                platforms[index] = null;
                freeIndices[freeCount++] = index;
            }
        }
        deferredDespawnsCount = 0;
    }

    // Component attachments (returns null safely if entity ID is stale)

    public void setBody(long id, BodyComponent body) {
        if (isAlive(id)) bodies[EntityId.getIndex(id)] = body;
    }

    public BodyComponent getBody(long id) {
        return isAlive(id) ? bodies[EntityId.getIndex(id)] : null;
    }

    public void setCollider(long id, ColliderComponent collider) {
        if (isAlive(id)) colliders[EntityId.getIndex(id)] = collider;
    }

    public ColliderComponent getCollider(long id) {
        return isAlive(id) ? colliders[EntityId.getIndex(id)] : null;
    }

    public void setFacing(long id, FacingComponent facing) {
        if (isAlive(id)) facings[EntityId.getIndex(id)] = facing;
    }

    public FacingComponent getFacing(long id) {
        return isAlive(id) ? facings[EntityId.getIndex(id)] : null;
    }

    public void setController(long id, ControllerComponent controller) {
        if (isAlive(id)) controllers[EntityId.getIndex(id)] = controller;
    }

    public ControllerComponent getController(long id) {
        return isAlive(id) ? controllers[EntityId.getIndex(id)] : null;
    }

    public void setInputState(long id, InputStateComponent inputState) {
        if (isAlive(id)) inputStates[EntityId.getIndex(id)] = inputState;
    }

    public InputStateComponent getInputState(long id) {
        return isAlive(id) ? inputStates[EntityId.getIndex(id)] : null;
    }

    public void setJumpState(long id, JumpStateComponent jumpState) {
        if (isAlive(id)) jumpStates[EntityId.getIndex(id)] = jumpState;
    }

    public JumpStateComponent getJumpState(long id) {
        return isAlive(id) ? jumpStates[EntityId.getIndex(id)] : null;
    }

    public void setPlatform(long id, com.makeasaying.engine.entity.components.PlatformComponent platform) {
        if (isAlive(id)) platforms[EntityId.getIndex(id)] = platform;
    }

    public com.makeasaying.engine.entity.components.PlatformComponent getPlatform(long id) {
        return isAlive(id) ? platforms[EntityId.getIndex(id)] : null;
    }

    // Fast indexed access for systems (iteration by index 1..MAX_ENTITIES)

    public boolean isAliveIndex(int index) {
        return index > 0 && index < MAX_ENTITIES && alive[index];
    }

    public long getIdByIndex(int index) {
        return EntityId.of(index, generations[index]);
    }

    public BodyComponent getBodyByIndex(int index) {
        return bodies[index];
    }

    public ColliderComponent getColliderByIndex(int index) {
        return colliders[index];
    }

    public FacingComponent getFacingByIndex(int index) {
        return facings[index];
    }

    public ControllerComponent getControllerByIndex(int index) {
        return controllers[index];
    }

    public InputStateComponent getInputStateByIndex(int index) {
        return inputStates[index];
    }

    public JumpStateComponent getJumpStateByIndex(int index) {
        return jumpStates[index];
    }

    public com.makeasaying.engine.entity.components.PlatformComponent getPlatformByIndex(int index) {
        return platforms[index];
    }

    public EntityEventQueue getEventQueue() {
        return eventQueue;
    }

    /**
     * Computes a 64-bit deterministic hash of all living entity components in index order.
     */
    public long stateHash() {
        long h = 1125899906842597L; // FNV-1a basis
        for (int i = 1; i < MAX_ENTITIES; i++) {
            if (!alive[i]) continue;
            h ^= i;
            h *= 1099511628211L;
            h ^= generations[i];
            h *= 1099511628211L;

            BodyComponent b = bodies[i];
            if (b != null) {
                h ^= b.x;
                h *= 1099511628211L;
                h ^= b.y;
                h *= 1099511628211L;
                h ^= Float.floatToIntBits(b.vx);
                h *= 1099511628211L;
                h ^= Float.floatToIntBits(b.vy);
                h *= 1099511628211L;
                h ^= Float.floatToIntBits(b.subpixelX);
                h *= 1099511628211L;
                h ^= Float.floatToIntBits(b.subpixelY);
                h *= 1099511628211L;
                h ^= (b.grounded ? 1 : 0);
                h *= 1099511628211L;
                h ^= b.supportId;
                h *= 1099511628211L;
            }

            FacingComponent f = facings[i];
            if (f != null) {
                h ^= f.direction;
                h *= 1099511628211L;
            }

            JumpStateComponent js = jumpStates[i];
            if (js != null) {
                h ^= js.coyoteTimer;
                h *= 1099511628211L;
                h ^= js.jumpBufferTimer;
                h *= 1099511628211L;
                h ^= js.dropThroughTimer;
                h *= 1099511628211L;
            }

            com.makeasaying.engine.entity.components.PlatformComponent p = platforms[i];
            if (p != null) {
                h ^= p.currentX;
                h *= 1099511628211L;
                h ^= p.currentY;
                h *= 1099511628211L;
                h ^= p.direction;
                h *= 1099511628211L;
                h ^= p.pauseTimer;
                h *= 1099511628211L;
                h ^= Float.floatToIntBits(p.remainderX);
                h *= 1099511628211L;
                h ^= Float.floatToIntBits(p.remainderY);
                h *= 1099511628211L;
            }
        }
        return h;
    }
}
