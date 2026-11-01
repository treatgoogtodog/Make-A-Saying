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
import com.makeasaying.engine.entity.events.EntityEventType;
import com.makeasaying.engine.input.ActionDefinition;
import com.makeasaying.engine.input.ActionRegistry;
import com.makeasaying.engine.input.Actions;
import com.makeasaying.engine.input.InputState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OneWayPlatformTest {
    private World world;
    private TileGrid grid;
    private MovementTuning tuning;
    private MovementSystem movementSystem;
    private ActionRegistry actionRegistry;

    private long entityId;
    private BodyComponent body;
    private ColliderComponent collider;
    private InputState inputState;
    private JumpStateComponent jumpState;

    @BeforeEach
    void setUp() {
        world = new World();
        grid = new TileGrid(30, 20, 16);
        tuning = MovementTuning.defaultTuning(60);
        movementSystem = new MovementSystem(tuning);

        actionRegistry = new ActionRegistry();
        actionRegistry.register(ActionDefinition.button(Actions.MOVE_LEFT));
        actionRegistry.register(ActionDefinition.button(Actions.MOVE_RIGHT));
        actionRegistry.register(ActionDefinition.button(Actions.MOVE_UP));
        actionRegistry.register(ActionDefinition.button(Actions.MOVE_DOWN));
        actionRegistry.register(ActionDefinition.button(Actions.JUMP));
        actionRegistry.register(ActionDefinition.axis(Actions.MOVE_X, Actions.MOVE_LEFT, Actions.MOVE_RIGHT));
        actionRegistry.register(ActionDefinition.axis(Actions.MOVE_Y, Actions.MOVE_DOWN, Actions.MOVE_UP));

        entityId = world.createEntity();
        body = new BodyComponent(64, 16);
        collider = new ColliderComponent(12, 36);
        jumpState = new JumpStateComponent();
        inputState = new InputState(actionRegistry);

        world.setBody(entityId, body);
        world.setCollider(entityId, collider);
        world.setFacing(entityId, new FacingComponent(1));
        world.setController(entityId, new ControllerComponent(ControlSource.PLAYER));
        world.setJumpState(entityId, jumpState);
        world.setInputState(entityId, new InputStateComponent(inputState));
        world.applyDeferredLifecycle();
    }

    private void tick() {
        inputState.resolveAxes();
        movementSystem.update(world, grid);
        world.getEventQueue().dispatch();
        world.applyDeferredLifecycle();
        inputState.beginTick();
    }

    @Test
    void testJumpUpThroughAndLandOnOneWay() {
        // Floor at ty=0
        for (int x = 0; x < 10; x++) grid.setTile(x, 0, TileShape.SOLID);
        // One-way platform at ty=2 (y: 32..47, top surface is y=48)
        for (int x = 2; x <= 6; x++) grid.setTile(x, 2, TileShape.ONE_WAY);

        AtomicInteger landedCount = new AtomicInteger(0);
        world.getEventQueue().subscribe(e -> {
            if (e.type() == EntityEventType.LANDED) landedCount.incrementAndGet();
        });

        // Entity on floor at x=64, y=16. Jump up high
        body.resetPosition(64, 16);
        body.grounded = true;

        inputState.setButtonDown(Actions.JUMP, true);
        tick();

        assertFalse(body.grounded);
        assertTrue(body.vy > 0f);

        // Advance through apex (>48px) and fall down onto one-way platform
        for (int i = 0; i < 40; i++) {
            tick();
            if (body.grounded) break;
        }

        assertTrue(body.grounded, "Entity must land on top of one-way platform");
        assertEquals(48, body.y, "Entity must rest precisely on top of one-way tile at y=48");
        assertEquals(1, landedCount.get(), "Must land once on one-way platform");
    }

    @Test
    void testDropThroughAndJumpBufferSuppression() {
        // Floor at ty=0, One-way platform at ty=2 (y=48)
        for (int x = 0; x < 10; x++) grid.setTile(x, 0, TileShape.SOLID);
        for (int x = 2; x <= 6; x++) grid.setTile(x, 2, TileShape.ONE_WAY);

        // Place entity grounded on one-way platform
        body.resetPosition(64, 48);
        body.grounded = true;
        tick();
        assertTrue(body.grounded);

        // Hold Down (move_y < 0) and press Jump
        inputState.setButtonDown(Actions.MOVE_DOWN, true);
        inputState.setButtonDown(Actions.JUMP, true);

        tick();

        // Must drop through: grounded becomes false, dropThroughTimer active, vy <= 0
        assertFalse(body.grounded, "Entity must drop through platform");
        assertTrue(body.y < 48, "Entity feet must drop below one-way platform");
        assertEquals(0, jumpState.jumpBufferTimer, "Jump buffer must be suppressed on drop-through");
        assertTrue(body.vy <= 0f, "Drop-through must not fire an upward jump");

        // Release buttons and fall down to floor (y=16)
        inputState.setButtonDown(Actions.MOVE_DOWN, false);
        inputState.setButtonDown(Actions.JUMP, false);

        for (int i = 0; i < 30; i++) {
            tick();
            if (body.grounded) break;
        }

        assertTrue(body.grounded, "Entity must land safely on floor below");
        assertEquals(16, body.y, "Must land on floor at y=16");
    }

    @Test
    void testNoSideBlockingOnOneWayPlatforms() {
        // Floor at ty=0 (y: 0..15)
        for (int x = 0; x < 15; x++) grid.setTile(x, 0, TileShape.SOLID);
        // One-way platform at ty=1 (y: 16..31) from tx=3..5 (x: 48..80)
        for (int x = 3; x <= 5; x++) grid.setTile(x, 1, TileShape.ONE_WAY);

        body.resetPosition(30, 16); // Feet on floor at y=16, collider (16..52) intersects one-way platform
        body.grounded = true;

        // Move right through the side of the one-way platform
        inputState.setButtonDown(Actions.MOVE_RIGHT, true);
        for (int i = 0; i < 20; i++) {
            tick();
        }

        // Entity must not be stopped horizontally by one-way side
        assertTrue(body.x > 50, "Entity must pass horizontally through one-way tiles without wall collision");
    }
}
