package com.makeasaying.engine.entity.systems;

import com.makeasaying.engine.entity.ControlSource;
import com.makeasaying.engine.entity.MovementTuning;
import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.engine.entity.TileShape;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.ControllerComponent;
import com.makeasaying.engine.entity.components.FacingComponent;
import com.makeasaying.engine.entity.components.InputStateComponent;
import com.makeasaying.engine.entity.components.JumpStateComponent;
import com.makeasaying.engine.entity.events.EntityEvent;
import com.makeasaying.engine.entity.events.EntityEventType;
import com.makeasaying.engine.input.ActionDefinition;
import com.makeasaying.engine.input.ActionRegistry;
import com.makeasaying.engine.input.Actions;
import com.makeasaying.engine.input.InputState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MovementCollisionTest {
    private World world;
    private TileGrid grid;
    private MovementTuning tuning;
    private MovementSystem movementSystem;
    private ActionRegistry actionRegistry;

    @BeforeEach
    void setUp() {
        world = new World();
        grid = new TileGrid(40, 30, 16);
        tuning = MovementTuning.defaultTuning(60);
        movementSystem = new MovementSystem(tuning);

        actionRegistry = new ActionRegistry();
        actionRegistry.register(ActionDefinition.button(Actions.MOVE_LEFT));
        actionRegistry.register(ActionDefinition.button(Actions.MOVE_RIGHT));
        actionRegistry.register(ActionDefinition.button(Actions.JUMP));
        actionRegistry.register(ActionDefinition.axis(Actions.MOVE_X, Actions.MOVE_LEFT, Actions.MOVE_RIGHT));
    }

    @Test
    void testAntiTunnelingAtMaxSpeedAgainstOneTileFloor() {
        // Single 1-tile floor at y = 5 (pixels 80..95), empty below it
        for (int x = 0; x < 40; x++) {
            grid.setTile(x, 5, TileShape.SOLID);
        }

        long id = world.createEntity();
        BodyComponent body = new BodyComponent(100, 150);
        ColliderComponent collider = new ColliderComponent(12, 36);

        // Maximum downward speed (360 px/s = 6 px/tick, or test extreme velocity 14 px/tick)
        body.vy = -14.0f;

        world.setBody(id, body);
        world.setCollider(id, collider);
        world.applyDeferredLifecycle();

        // Step physics repeatedly
        for (int i = 0; i < 20; i++) {
            movementSystem.update(world, grid);
        }

        // Entity must stop precisely on top of the 1-tile floor (y = 96) and NOT tunnel through!
        assertEquals(96, body.y, "Feet must land exactly on top of floor at y=96");
        assertTrue(body.grounded);
        assertEquals(0f, body.vy);
    }

    @Test
    void testAntiTunnelingAtMaxSpeedAgainstOneTileWall() {
        // Single 1-tile vertical wall at tile x = 10 (px 160..175), empty behind it
        for (int y = 0; y < 30; y++) {
            grid.setTile(10, y, TileShape.SOLID);
        }

        long id = world.createEntity();
        BodyComponent body = new BodyComponent(100, 100);
        ColliderComponent collider = new ColliderComponent(12, 36);

        // Extreme horizontal velocity (14 px/tick)
        body.vx = 14.0f;

        world.setBody(id, body);
        world.setCollider(id, collider);
        world.applyDeferredLifecycle();

        for (int i = 0; i < 20; i++) {
            movementSystem.update(world, grid);
        }

        // Right edge of collider (body.x + 6) must stop at x = 160, never passing into tile 11!
        assertEquals(160, body.x + collider.getRight(0));
        assertEquals(0f, body.vx);
    }

    @Test
    void testMirrorSymmetryBetweenLeftAndRightMovement() {
        // Two entities on an infinite flat floor: one moves left, one moves right
        for (int x = 0; x < 40; x++) grid.setTile(x, 0, TileShape.SOLID);

        int origin = 200;

        long leftId = world.createEntity();
        BodyComponent leftBody = new BodyComponent(origin, 16);
        InputState leftInput = new InputState(actionRegistry);
        leftInput.setButtonDown(Actions.MOVE_LEFT, true);
        leftInput.resolveAxes();
        world.setBody(leftId, leftBody);
        world.setCollider(leftId, new ColliderComponent(12, 36));
        world.setInputState(leftId, new InputStateComponent(leftInput));

        long rightId = world.createEntity();
        BodyComponent rightBody = new BodyComponent(origin, 16);
        InputState rightInput = new InputState(actionRegistry);
        rightInput.setButtonDown(Actions.MOVE_RIGHT, true);
        rightInput.resolveAxes();
        world.setBody(rightId, rightBody);
        world.setCollider(rightId, new ColliderComponent(12, 36));
        world.setInputState(rightId, new InputStateComponent(rightInput));

        world.applyDeferredLifecycle();

        // Run for 30 ticks
        for (int tick = 1; tick <= 30; tick++) {
            movementSystem.update(world, grid);

            int distLeft = origin - leftBody.x;
            int distRight = rightBody.x - origin;

            // Strict DEC-009 symmetric rounding: distances must match every single tick
            assertEquals(distLeft, distRight, "Tick " + tick + ": Left and right moved distance must be identical");
            assertEquals(-leftBody.vx, rightBody.vx, 0.0001f);
            assertEquals(-leftBody.subpixelX, rightBody.subpixelX, 0.0001f);
        }
    }

    @Test
    void testLandedEventFiresOncePerTouchdown() {
        // Floor at y = 0
        for (int x = 0; x < 40; x++) grid.setTile(x, 0, TileShape.SOLID);

        long id = world.createEntity();
        BodyComponent body = new BodyComponent(100, 60); // In air
        world.setBody(id, body);
        world.setCollider(id, new ColliderComponent(12, 36));
        world.applyDeferredLifecycle();

        List<EntityEvent> landedEvents = new ArrayList<>();
        world.getEventQueue().subscribe(e -> {
            if (e.type() == EntityEventType.LANDED) {
                landedEvents.add(e);
            }
        });

        // Fall to floor and remain grounded for 10 ticks
        for (int i = 0; i < 20; i++) {
            movementSystem.update(world, grid);
            world.getEventQueue().dispatch();
        }

        assertTrue(body.grounded);
        // Landed event must fire strictly ONCE upon touchdown, not on subsequent grounded ticks!
        assertEquals(1, landedEvents.size(), "LANDED event must fire exactly once");
        assertEquals(id, landedEvents.get(0).entityId());
    }
}
