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
import com.makeasaying.engine.input.ActionDefinition;
import com.makeasaying.engine.input.ActionRegistry;
import com.makeasaying.engine.input.Actions;
import com.makeasaying.engine.input.InputState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MovementSystemTest {
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
        // Build floor at y = 0
        for (int x = 0; x < 30; x++) {
            grid.setTile(x, 0, TileShape.SOLID);
        }

        tuning = MovementTuning.defaultTuning(60);
        movementSystem = new MovementSystem(tuning);

        actionRegistry = new ActionRegistry();
        actionRegistry.register(ActionDefinition.button(Actions.MOVE_LEFT));
        actionRegistry.register(ActionDefinition.button(Actions.MOVE_RIGHT));
        actionRegistry.register(ActionDefinition.button(Actions.JUMP));
        actionRegistry.register(ActionDefinition.axis(Actions.MOVE_X, Actions.MOVE_LEFT, Actions.MOVE_RIGHT));

        entityId = world.createEntity();
        body = new BodyComponent(64, 16); // Feet on floor
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
    }

    @Test
    void testRunAccelerationAndMaxSpeedClamp() {
        inputState.setButtonDown(Actions.MOVE_RIGHT, true);

        // Advance 20 ticks
        for (int i = 0; i < 20; i++) {
            tick();
        }

        // At 120 px/s with 60 Hz, maxRunSpeedPerTick is 2.0 px/tick
        assertEquals(tuning.maxRunSpeedPerTick(), body.vx, 0.001f);
        assertTrue(body.x > 64);
    }

    @Test
    void testFrictionStop() {
        // Accelerate to max speed
        inputState.setButtonDown(Actions.MOVE_RIGHT, true);
        for (int i = 0; i < 15; i++) tick();
        assertEquals(tuning.maxRunSpeedPerTick(), body.vx, 0.001f);

        // Release input
        inputState.setButtonDown(Actions.MOVE_RIGHT, false);
        for (int i = 0; i < 15; i++) tick();

        // Must come to a complete stop
        assertEquals(0f, body.vx, 0.0001f);
    }

    @Test
    void testTurnaroundAcceleration() {
        inputState.setButtonDown(Actions.MOVE_RIGHT, true);
        for (int i = 0; i < 15; i++) tick();
        assertEquals(tuning.maxRunSpeedPerTick(), body.vx, 0.001f);

        // Reverse to LEFT
        inputState.setButtonDown(Actions.MOVE_RIGHT, false);
        inputState.setButtonDown(Actions.MOVE_LEFT, true);

        tick();
        // Turnaround acceleration should rapidly decelerate vx
        assertTrue(body.vx < tuning.maxRunSpeedPerTick());
    }

    @Test
    void testJumpApexHeightAndDuration() {
        // First tick to detect grounded
        tick();
        assertTrue(body.grounded);

        // Press Jump
        inputState.setButtonDown(Actions.JUMP, true);
        tick();

        assertFalse(body.grounded);
        assertEquals(tuning.jumpSpeedPerTick(), body.vy, 0.001f);

        int initialY = 16;
        int maxY = initialY;
        int riseTicks = 0;

        // Keep holding jump until apex
        while (body.vy > 0) {
            tick();
            if (body.y > maxY) maxY = body.y;
            riseTicks++;
        }

        // Verify apex height ~30-36 pixels above floor
        int jumpHeight = maxY - initialY;
        assertTrue(jumpHeight >= 28 && jumpHeight <= 36, "Jump apex height should be ~32px, was: " + jumpHeight);
        assertTrue(riseTicks >= 15 && riseTicks <= 22, "Rise duration should be ~18 ticks, was: " + riseTicks);
    }

    @Test
    void testVariableJumpCut() {
        tick(); // establish grounded

        // Full jump
        inputState.setButtonDown(Actions.JUMP, true);
        tick();
        float fullJumpVy1 = body.vy;

        // Reset
        body.resetPosition(64, 16);
        tick();

        // Jump with early release (cut)
        inputState.setButtonDown(Actions.JUMP, true);
        tick();
        inputState.setButtonDown(Actions.JUMP, false); // release immediately
        tick();

        // Upward velocity after release must be cut
        assertTrue(body.vy < fullJumpVy1 * tuning.jumpCutMultiplier() + 0.1f);
    }

    @Test
    void testCoyoteTime() {
        // Walk off a ledge: create gap in floor from x = 10..20
        grid.clear();
        for (int x = 0; x <= 5; x++) grid.setTile(x, 0, TileShape.SOLID);

        body.resetPosition(80, 16); // on solid tile 5
        tick();
        assertTrue(body.grounded);

        // Walk right off ledge into air: tile 5 ends at 95, collider left edge is body.x - 6.
        // At body.x = 110, left is 104, completely past tile 5.
        body.x = 110;
        tick();
        assertFalse(body.grounded);
        assertTrue(jumpState.coyoteTimer > 0, "Coyote timer must be active after walking off ledge");

        // Jump while airborne within coyote window
        inputState.setButtonDown(Actions.JUMP, true);
        tick();
        assertEquals(tuning.jumpSpeedPerTick(), body.vy, 0.001f, "Jump must succeed during coyote window");

        // Coyote timer must be zeroed immediately (no double jump)
        assertEquals(0, jumpState.coyoteTimer);

        // Try second jump in mid-air -> must fail
        inputState.beginTick();
        inputState.setButtonDown(Actions.JUMP, true);
        tick();
        assertTrue(body.vy < tuning.jumpSpeedPerTick(), "Second air jump must not be allowed");
    }

    @Test
    void testJumpBuffer() {
        // Entity in air falling
        body.resetPosition(64, 40);
        body.grounded = false;
        body.vy = -2.0f;

        // Press jump while still in air 3 ticks before landing
        inputState.setButtonDown(Actions.JUMP, true);
        tick();
        assertTrue(jumpState.jumpBufferTimer > 0);

        // Move to touchdown on ground
        body.y = 16;
        tick();

        // Buffered jump should immediately fire on landing
        assertTrue(body.vy > 0f, "Buffered jump should trigger upon landing");
    }

    @Test
    void testCeilingCornerCorrection() {
        // Build ceiling with a 1-tile gap
        // Ceiling at y = 4 (tiles 0..3 solid, tile 4 empty, tiles 5..10 solid)
        for (int x = 0; x <= 3; x++) grid.setTile(x, 4, TileShape.SOLID);
        // tile 4 (x: 64..79) is EMPTY!
        for (int x = 5; x <= 10; x++) grid.setTile(x, 4, TileShape.SOLID);

        // Place character underneath with collider (12px wide: -6 to +6, 36px tall)
        // Position at x = 68: left edge is 62 (2px inside solid tile 3 which ends at 63).
        // Feet at y = 26: top is at 26+36=62 (2px below ceiling row 4 at y=64).
        body.resetPosition(68, 26);
        body.vy = 4.0f; // jumping upward
        inputState.setButtonDown(Actions.JUMP, true); // Keep holding jump to prevent variable cut

        tick();

        // Corner correction should push character right into the open gap (x == 70)
        assertEquals(70, body.x, "Character should be pushed right by corner correction to clear ceiling");
        assertTrue(body.vy > 0, "Character should continue moving upward without bonking ceiling");
    }

    @Test
    void testWallCollisionStopsVelocity() {
        // Wall at x = 10 (px 160)
        for (int y = 0; y < 10; y++) grid.setTile(10, y, TileShape.SOLID);

        body.resetPosition(150, 16);
        body.vx = 8.0f;

        tick();

        // Should hit wall and stop
        assertEquals(0f, body.vx, 0.001f);
        assertTrue(body.x + collider.getRight(0) <= 160);
    }
}
