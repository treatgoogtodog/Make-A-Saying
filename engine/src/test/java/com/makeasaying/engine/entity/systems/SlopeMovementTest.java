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

class SlopeMovementTest {
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
        actionRegistry.register(ActionDefinition.button(Actions.JUMP));
        actionRegistry.register(ActionDefinition.axis(Actions.MOVE_X, Actions.MOVE_LEFT, Actions.MOVE_RIGHT));

        entityId = world.createEntity();
        body = new BodyComponent(32, 16);
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
    void testNoSlidingAtRestOnSlopes() {
        // Build slope UP_RIGHT at (tx=2, ty=1)
        grid.setTile(2, 0, TileShape.SOLID);
        grid.setTile(2, 1, TileShape.SLOPE_45_UP_RIGHT);

        // Place on slope at x=40, y=25
        body.resetPosition(40, 25);
        body.grounded = true;

        for (int i = 0; i < 30; i++) {
            tick();
        }

        // Entity must not slide down or move horizontally when at rest
        assertEquals(40, body.x, "Entity must not slide horizontally at rest");
        assertEquals(25, body.y, "Entity must not slide vertically at rest");
        assertTrue(body.grounded);
    }

    @Test
    void testWalkingUpAndDownSlopes() {
        // Floor at ty=0 from tx=0..4
        for (int x = 0; x <= 4; x++) grid.setTile(x, 0, TileShape.SOLID);
        // Slope UP_RIGHT at (tx=2, ty=1), (tx=2, ty=0) is SOLID fill
        grid.setTile(2, 1, TileShape.SLOPE_45_UP_RIGHT);
        // Solid flat ground at ty=1 from tx=3..4
        grid.setTile(3, 1, TileShape.SOLID);
        grid.setTile(4, 1, TileShape.SOLID);

        // Start at flat ground x=25, y=16
        body.resetPosition(25, 16);
        body.grounded = true;

        // Walk right up slope (bounded loop, max 40 ticks)
        inputState.setButtonDown(Actions.MOVE_RIGHT, true);
        for (int i = 0; i < 40 && body.x < 55; i++) {
            tick();
            assertTrue(body.grounded, "Entity must stay grounded while walking up slope");
        }

        // At tx=3, top of slope is at y=32
        assertEquals(32, body.y, "Entity should reach top flat ground at y=32");
        assertTrue(body.x >= 50, "Entity should advance onto flat ground at top of slope");

        // Now walk left back down slope (bounded loop, max 40 ticks)
        inputState.setButtonDown(Actions.MOVE_RIGHT, false);
        inputState.setButtonDown(Actions.MOVE_LEFT, true);

        for (int i = 0; i < 40 && body.x > 25; i++) {
            tick();
            assertTrue(body.grounded, "Entity must stay grounded while walking down slope via snapping");
        }

        // Back on original floor
        assertEquals(16, body.y, "Entity should descend smoothly back to floor at y=16");
    }

    @Test
    void testMirrorSymmetryBetweenUpRightAndUpLeft() {
        // Setup UP_RIGHT slope at tx=2 (x: 32..47)
        TileGrid gridRight = new TileGrid(20, 10, 16);
        gridRight.setTile(1, 0, TileShape.SOLID);
        gridRight.setTile(2, 0, TileShape.SOLID);
        gridRight.setTile(2, 1, TileShape.SLOPE_45_UP_RIGHT);

        // Setup UP_LEFT slope at tx=5 (x: 80..95)
        TileGrid gridLeft = new TileGrid(20, 10, 16);
        gridLeft.setTile(5, 0, TileShape.SOLID);
        gridLeft.setTile(5, 1, TileShape.SLOPE_45_UP_LEFT);
        gridLeft.setTile(6, 0, TileShape.SOLID);

        // Test sample height at identical distance from slope base
        // For UP_RIGHT: local column c=4 in tile tx=2 (world x = 32 + 4 = 36)
        // For UP_LEFT: mirrored local column in tile tx=5:
        // Collider middle columns are feetX - 1 and feetX.
        // On UP_RIGHT at feetX=36, columns are 35 (lx=3) and 36 (lx=4).
        // On UP_LEFT, the mirrored columns are lx=11 and lx=12, so feetX is 80 + 12 = 92.
        int surfRight = MovementSystem.getFloorSurface(null, gridRight, 36, 16, 4, 16, false);
        int surfLeft = MovementSystem.getFloorSurface(null, gridLeft, 92, 16, 4, 16, false);

        assertEquals(surfRight, surfLeft, "Floor height at mirrored slope columns must be identical");
    }

    @Test
    void testWalkingOffSlopeTopIntoAirNoLaunch() {
        // Slope UP_RIGHT at (tx=2, ty=1), gap to the right (tx=3 is EMPTY down to abyss)
        grid.setTile(2, 0, TileShape.SOLID);
        grid.setTile(2, 1, TileShape.SLOPE_45_UP_RIGHT);

        // Start near top of slope at x=46, y=31
        body.resetPosition(46, 31);
        body.grounded = true;

        inputState.setButtonDown(Actions.MOVE_RIGHT, true);

        // Advance until walked off top into empty space (bounded loop)
        for (int i = 0; i < 20 && body.x < 52; i++) {
            tick();
        }

        assertFalse(body.grounded, "Entity must become airborne when walking off slope top");
        // Must not launch upward (vy <= 0)
        assertTrue(body.vy <= 0f, "Entity must have vy <= 0 (no upward hop) when walking off slope top");
    }

    @Test
    void testNoStickToFloorBeyondSlopeSnapPx() {
        // Elevated platform at ty=1 (y=16..31, top=32) from tx=0..2 (ending at x=48)
        for (int x = 0; x <= 2; x++) {
            grid.setTile(x, 1, TileShape.SOLID);
        }

        body.resetPosition(40, 32);
        body.grounded = true;

        // Walk right off the cliff: drop to floor below is > slopeSnapPx (4)
        inputState.setButtonDown(Actions.MOVE_RIGHT, true);
        for (int i = 0; i < 15; i++) tick();

        // Must become airborne, NOT snapped down to distant floor
        assertFalse(body.grounded, "Entity must not stick to floor beyond slopeSnapPx");
    }

    @Test
    void testJumpOffSlopeAndLandOnSlope() {
        grid.setTile(2, 0, TileShape.SOLID);
        grid.setTile(2, 1, TileShape.SLOPE_45_UP_RIGHT);
        grid.setTile(3, 0, TileShape.SOLID);
        grid.setTile(3, 1, TileShape.SLOPE_45_UP_RIGHT);

        body.resetPosition(40, 25);
        body.grounded = true;

        AtomicInteger landedCount = new AtomicInteger(0);
        world.getEventQueue().subscribe(e -> {
            if (e.type() == EntityEventType.LANDED) landedCount.incrementAndGet();
        });

        // Jump upward
        inputState.setButtonDown(Actions.JUMP, true);
        tick();

        assertFalse(body.grounded, "Must leave ground on jump");
        assertTrue(body.vy > 0f);

        inputState.setButtonDown(Actions.JUMP, false);

        // Fall back down onto slope
        for (int i = 0; i < 40; i++) {
            tick();
            if (body.grounded) break;
        }

        assertTrue(body.grounded, "Must land back on slope");
        assertEquals(1, landedCount.get(), "LANDED event must fire exactly once on slope touchdown");
        assertEquals(25, body.y, "Must land precisely on slope surface height");
    }

    @Test
    void testPassingUpwardThroughSlopeTile() {
        // Slope tile at (tx=2, ty=2) (y: 32..47)
        grid.setTile(2, 2, TileShape.SLOPE_45_UP_RIGHT);

        // Entity jumping up from below (y=16) with full upward speed
        body.resetPosition(40, 16);
        body.grounded = false;
        body.vy = 8.0f;
        inputState.setButtonDown(Actions.JUMP, true); // Hold jump to prevent early release cut

        // Move upward through slope
        for (int i = 0; i < 6; i++) {
            tick();
        }

        // Must not be stopped or bonked; passes cleanly through slope
        assertTrue(body.y > 32, "Entity must pass upward through slope tile without collision");
    }

    @Test
    void testAntiTunnelingAtMaxSpeedOnSlope() {
        grid.setTile(2, 0, TileShape.SOLID);
        grid.setTile(2, 1, TileShape.SLOPE_45_UP_RIGHT);

        // Position directly above slope at x=40 (surface is y=25)
        // Falling at maximum fall speed (360 px/s = 6.0 px/tick)
        body.resetPosition(40, 30);
        body.grounded = false;
        body.vy = -tuning.maxFallSpeedPerTick();

        tick();

        assertTrue(body.grounded, "Entity falling at max speed must land on slope");
        assertEquals(25, body.y, "Entity must not tunnel through slope; must stop exactly at surface height");
        assertEquals(0f, body.vy, 0.001f);
    }

    @Test
    void testWallTallerThanSlopeTopBlocksMovement() {
        // Floor at ty=0
        for (int x = 0; x <= 4; x++) grid.setTile(x, 0, TileShape.SOLID);
        // Slope UP_RIGHT at (tx=2, ty=1)
        grid.setTile(2, 1, TileShape.SLOPE_45_UP_RIGHT);
        // Wall at tx=3: ground at ty=1 (y: 16..31) AND wall above at ty=2 (y: 32..47)
        grid.setTile(3, 1, TileShape.SOLID);
        grid.setTile(3, 2, TileShape.SOLID);

        body.resetPosition(25, 16);
        body.grounded = true;

        inputState.setButtonDown(Actions.MOVE_RIGHT, true);
        for (int i = 0; i < 40; i++) {
            tick();
        }

        // Entity must be stopped by the upper wall at tx=3 (x=48)
        // Collider right edge (body.x + 6) must not penetrate x=48
        assertTrue(body.x + collider.getRight(0) <= 48,
            "Solid wall taller than slope top must block horizontal movement");
    }

    @Test
    void testAirborneEntityBlockedBySolidTileAtSlopeSeam() {
        // Slope UP_RIGHT at (tx=2, ty=1), solid ground at (tx=3, ty=1)
        grid.setTile(2, 1, TileShape.SLOPE_45_UP_RIGHT);
        grid.setTile(3, 1, TileShape.SOLID);

        // Airborne entity at y=24 flying right toward the solid tile at tx=3 (x=48..63, y: 16..31)
        body.resetPosition(40, 24);
        body.grounded = false;
        body.vx = 2.0f; // in-flight horizontal speed

        // Simulate tick without input down (flying forward)
        tick();

        // Must collide with solid tile at x=48 and be stopped as a wall
        assertTrue(body.x + collider.getRight(0) <= 48,
            "Airborne entity must collide with solid tile even if adjacent to slope");
    }

    @Test
    void testSlopeSeamWorksForUpLeftSlope() {
        // Floor at ty=0 from tx=0..4
        for (int x = 0; x <= 4; x++) grid.setTile(x, 0, TileShape.SOLID);
        // Solid flat ground at ty=1 from tx=0..1 (top=32)
        grid.setTile(0, 1, TileShape.SOLID);
        grid.setTile(1, 1, TileShape.SOLID);
        // Slope UP_LEFT at (tx=2, ty=1)
        grid.setTile(2, 1, TileShape.SLOPE_45_UP_LEFT);

        // Start on elevated flat ground at x=20, y=32
        body.resetPosition(20, 32);
        body.grounded = true;

        // Walk right down slope
        inputState.setButtonDown(Actions.MOVE_RIGHT, true);
        for (int i = 0; i < 40 && body.x < 55; i++) {
            tick();
            assertTrue(body.grounded, "Must stay grounded walking down UP_LEFT slope");
        }
        assertEquals(16, body.y, "Must descend to bottom floor at y=16");

        // Now walk left back up slope onto flat ground at y=32
        inputState.setButtonDown(Actions.MOVE_RIGHT, false);
        inputState.setButtonDown(Actions.MOVE_LEFT, true);
        for (int i = 0; i < 40 && body.x > 20; i++) {
            tick();
            assertTrue(body.grounded, "Must stay grounded walking up UP_LEFT slope");
        }

        assertEquals(32, body.y, "Entity must reach elevated flat ground at y=32 on UP_LEFT slope");
        assertTrue(body.x <= 25, "Entity should advance onto flat ground at top of UP_LEFT slope");
    }

    @Test
    void testMovementLevelMirrorSymmetryOnSlopes() {
        // Room A: Slope UP_RIGHT at tx=2 (x: 32..47, y: 16..31)
        TileGrid gridA = new TileGrid(10, 5, 16);
        for (int x = 0; x < 10; x++) gridA.setTile(x, 0, TileShape.SOLID);
        gridA.setTile(2, 1, TileShape.SLOPE_45_UP_RIGHT);
        for (int x = 3; x < 10; x++) gridA.setTile(x, 1, TileShape.SOLID);

        // Room B: Exact horizontal mirror (width = 160 px, 10 tiles)
        // Mirrored tx=2 is 10 - 1 - 2 = 7. Slope UP_LEFT at tx=7 (x: 112..127)
        TileGrid gridB = new TileGrid(10, 5, 16);
        for (int x = 0; x < 10; x++) gridB.setTile(x, 0, TileShape.SOLID);
        gridB.setTile(7, 1, TileShape.SLOPE_45_UP_LEFT);
        for (int x = 0; x <= 6; x++) gridB.setTile(x, 1, TileShape.SOLID);

        World worldA = new World();
        World worldB = new World();

        long idA = worldA.createEntity();
        long idB = worldB.createEntity();

        int startXA = 25;
        int startXB = 160 - startXA; // 135
        int startY = 16;

        BodyComponent bodyA = new BodyComponent(startXA, startY);
        BodyComponent bodyB = new BodyComponent(startXB, startY);

        ColliderComponent colA = new ColliderComponent(12, 36);
        ColliderComponent colB = new ColliderComponent(12, 36);

        JumpStateComponent jumpA = new JumpStateComponent();
        JumpStateComponent jumpB = new JumpStateComponent();

        InputState inA = new InputState(actionRegistry);
        InputState inB = new InputState(actionRegistry);

        worldA.setBody(idA, bodyA);
        worldA.setCollider(idA, colA);
        worldA.setFacing(idA, new FacingComponent(1));
        worldA.setController(idA, new ControllerComponent(ControlSource.PLAYER));
        worldA.setJumpState(idA, jumpA);
        worldA.setInputState(idA, new InputStateComponent(inA));
        worldA.applyDeferredLifecycle();

        worldB.setBody(idB, bodyB);
        worldB.setCollider(idB, colB);
        worldB.setFacing(idB, new FacingComponent(-1));
        worldB.setController(idB, new ControllerComponent(ControlSource.PLAYER));
        worldB.setJumpState(idB, jumpB);
        worldB.setInputState(idB, new InputStateComponent(inB));
        worldB.applyDeferredLifecycle();

        // Walk up slopes: A moves right, B moves left
        inA.setButtonDown(Actions.MOVE_RIGHT, true);
        inB.setButtonDown(Actions.MOVE_LEFT, true);

        for (int t = 0; t < 30; t++) {
            inA.resolveAxes();
            inB.resolveAxes();
            movementSystem.update(worldA, gridA);
            movementSystem.update(worldB, gridB);
            worldA.getEventQueue().dispatch();
            worldB.getEventQueue().dispatch();
            inA.beginTick();
            inB.beginTick();

            assertEquals(160, bodyA.x + bodyB.x, "Tick " + t + ": Mirrored X coordinates must sum to room width 160");
            assertEquals(bodyA.y, bodyB.y, "Tick " + t + ": Y coordinate must be identical on mirrored slopes");
            assertEquals(bodyA.grounded, bodyB.grounded, "Tick " + t + ": Grounded state must match");
        }
    }
}
