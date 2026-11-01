package com.makeasaying.engine.entity;

import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldTest {
    private World world;

    @BeforeEach
    void setUp() {
        world = new World();
    }

    @Test
    void testNullEntityIdHandling() {
        assertEquals(EntityId.NULL_ID, 0L);
        assertTrue(EntityId.isNull(0L));
        assertFalse(EntityId.isValid(0L));
        assertFalse(world.isAlive(EntityId.NULL_ID));

        assertNull(world.getBody(EntityId.NULL_ID));
        assertNull(world.getCollider(EntityId.NULL_ID));
    }

    @Test
    void testStaleEntityIdCleanHandling() {
        long id1 = world.createEntity();
        world.setBody(id1, new BodyComponent(10, 20));
        world.applyDeferredLifecycle();

        assertTrue(world.isAlive(id1));
        assertNotNull(world.getBody(id1));

        // Despawn id1
        world.destroyEntity(id1);
        world.applyDeferredLifecycle();

        // id1 is now dead
        assertFalse(world.isAlive(id1));
        assertNull(world.getBody(id1)); // Stale ID safely returns null

        // Create new entity reusing the slot
        long id2 = world.createEntity();
        world.applyDeferredLifecycle();

        assertEquals(EntityId.getIndex(id1), EntityId.getIndex(id2), "Slot index should be recycled");
        assertNotEquals(EntityId.getGeneration(id1), EntityId.getGeneration(id2), "Generation must increment");

        // Old id1 must remain dead even after slot reuse
        assertFalse(world.isAlive(id1));
        assertTrue(world.isAlive(id2));
    }

    @Test
    void testStateHashAndReset() {
        long hashInitial = world.stateHash();

        long id = world.createEntity();
        world.setBody(id, new BodyComponent(32, 16));
        world.setCollider(id, new ColliderComponent(12, 36));
        world.applyDeferredLifecycle();

        long hashWithEntity = world.stateHash();
        assertNotEquals(hashInitial, hashWithEntity);

        // Reset world
        world.reset();
        assertEquals(hashInitial, world.stateHash());
    }
}
