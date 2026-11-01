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
import com.makeasaying.engine.entity.components.PlatformComponent;
import com.makeasaying.engine.entity.events.EntityEventType;
import com.makeasaying.engine.input.ActionDefinition;
import com.makeasaying.engine.input.ActionRegistry;
import com.makeasaying.engine.input.Actions;
import com.makeasaying.engine.input.InputState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MovingPlatformTest {
    private World world;
    private TileGrid grid;
    private MovementTuning tuning;
    private PlatformSystem platformSystem;
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
        platformSystem = new PlatformSystem();
        movementSystem = new MovementSystem(tuning);

        actionRegistry = new ActionRegistry();
        actionRegistry.register(ActionDefinition.button(Actions.MOVE_LEFT));
        actionRegistry.register(ActionDefinition.button(Actions.MOVE_RIGHT));
        actionRegistry.register(ActionDefinition.button(Actions.JUMP));
        actionRegistry.register(ActionDefinition.axis(Actions.MOVE_X, Actions.MOVE_LEFT, Actions.MOVE_RIGHT));

        entityId = world.createEntity();
        body = new BodyComponent(64, 48);
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
        // Update Order: Step 4 PlatformSystem, Step 5 MovementSystem
        platformSystem.update(world, grid);
        movementSystem.update(world, grid);
        world.getEventQueue().dispatch();
        world.applyDeferredLifecycle();
        inputState.beginTick();
    }

    @Test
    void testHorizontalPlatformCarry() {
        // Platform moving from (50, 32) to (120, 32) at 2 px/tick
        long platId = world.createEntity();
        PlatformComponent plat = new PlatformComponent(50, 32, 120, 32, 48, 16, 2.0f);
        world.setPlatform(platId, plat);
        world.applyDeferredLifecycle();

        // Place entity on top of platform at x=70, y=48
        body.resetPosition(70, 48);
        body.grounded = true;
        body.supportId = platId;

        // Advance 10 ticks
        for (int i = 0; i < 10; i++) {
            tick();
        }

        // Platform moved 20 pixels right; rider should be carried 20 pixels right
        assertEquals(70, plat.currentX);
        assertEquals(90, body.x, "Rider must be carried horizontally by moving platform");
        assertEquals(48, body.y);
        assertTrue(body.grounded);
        assertEquals(platId, body.supportId);
    }

    @Test
    void testVerticalPlatformDescendingAtMaxSpeedKeepsGroundedWithSingleLanded() {
        // Platform descending from y=100 down to y=40 at 3.0 px/tick
        long platId = world.createEntity();
        PlatformComponent plat = new PlatformComponent(60, 100, 60, 40, 48, 16, 3.0f);
        world.setPlatform(platId, plat);
        world.applyDeferredLifecycle();

        // Place rider on top at y=116
        body.resetPosition(80, 116);
        body.grounded = true;
        body.supportId = platId;

        AtomicInteger landedCount = new AtomicInteger(0);
        world.getEventQueue().subscribe(e -> {
            if (e.type() == EntityEventType.LANDED) landedCount.incrementAndGet();
        });

        // Descend for 10 ticks
        for (int i = 0; i < 10; i++) {
            tick();
            assertTrue(body.grounded, "Rider must remain continuously grounded while descending");
            assertEquals(plat.currentY + plat.height, body.y, "Rider feet must match platform top");
        }

        // Because rider never became airborne during descent, LANDED must NOT fire repeatedly
        assertEquals(0, landedCount.get(), "LANDED must not fire repeatedly while continuously grounded on descending platform");
    }

    @Test
    void testRiderBlockedByWallWhileCarried() {
        // Wall at tx=7 (x=112)
        for (int y = 0; y < 10; y++) grid.setTile(7, y, TileShape.SOLID);

        // Platform moving right past x=112 (platform is below wall or moving toward it)
        long platId = world.createEntity();
        PlatformComponent plat = new PlatformComponent(60, 16, 120, 16, 64, 16, 2.0f);
        world.setPlatform(platId, plat);
        world.applyDeferredLifecycle();

        body.resetPosition(80, 32);
        body.grounded = true;
        body.supportId = platId;

        // Carry toward wall
        for (int i = 0; i < 25; i++) {
            tick();
        }

        // Wall at 112 stops rider (collider right edge is x + 6 <= 112 -> x <= 106)
        assertTrue(body.x + collider.getRight(0) <= 112, "Wall must stop carried rider");
    }

    @Test
    void testPlatformPushesEntity() {
        // Horizontal platform pushing an airborne/standing entity
        long platId = world.createEntity();
        PlatformComponent plat = new PlatformComponent(20, 16, 80, 16, 32, 32, 2.0f);
        world.setPlatform(platId, plat);
        world.applyDeferredLifecycle();

        // Place entity in the path of the platform at x=54, y=20
        body.resetPosition(54, 20);

        // Advance 10 ticks (platform moves from 20 to 40, width 32, right edge reaches 72)
        for (int i = 0; i < 10; i++) {
            tick();
        }

        // Entity must have been pushed right beyond the platform's right edge
        assertTrue(body.x + collider.getLeft(0) >= plat.currentX + plat.width,
            "Entity must be pushed ahead of moving platform");
    }

    @Test
    void testBlockCrushModeStopsPlatform() {
        // Solid wall at tx=5 (x=80)
        for (int y = 0; y < 10; y++) grid.setTile(5, y, TileShape.SOLID);

        // Platform moving right toward wall with CrushMode.BLOCK
        long platId = world.createEntity();
        PlatformComponent plat = new PlatformComponent(30, 16, 70, 16, 32, 32, 2.0f, 0, PlatformComponent.CrushMode.BLOCK);
        world.setPlatform(platId, plat);
        world.applyDeferredLifecycle();

        // Place entity between platform and wall at x=70
        body.resetPosition(70, 20);

        for (int i = 0; i < 20; i++) {
            tick();
        }

        // Platform must NOT push entity through solid wall; it must BLOCK
        assertTrue(plat.currentX + plat.width < 80, "BLOCK platform must halt before crushing entity into wall");
        assertTrue(body.x + collider.getRight(0) <= 80, "Entity must not be pushed into solid wall");
    }

    @Test
    void testCrushModeEmitsCrushedEvent() {
        // Solid wall at tx=5 (x=80)
        for (int y = 0; y < 10; y++) grid.setTile(5, y, TileShape.SOLID);

        // Platform with CrushMode.CRUSH
        long platId = world.createEntity();
        PlatformComponent plat = new PlatformComponent(30, 16, 70, 16, 32, 32, 2.0f, 0, PlatformComponent.CrushMode.CRUSH);
        world.setPlatform(platId, plat);
        world.applyDeferredLifecycle();

        body.resetPosition(70, 20);

        AtomicInteger crushedCount = new AtomicInteger(0);
        java.util.concurrent.atomic.AtomicLong crushedVictimId = new java.util.concurrent.atomic.AtomicLong(0);
        java.util.concurrent.atomic.AtomicLong crushedPlatformId = new java.util.concurrent.atomic.AtomicLong(0);
        world.getEventQueue().subscribe(e -> {
            if (e.type() == EntityEventType.CRUSHED) {
                crushedCount.incrementAndGet();
                crushedVictimId.set(e.entityId());
                crushedPlatformId.set(e.targetId());
            }
        });

        for (int i = 0; i < 20; i++) {
            tick();
        }

        assertTrue(crushedCount.get() > 0, "CRUSH platform must emit CRUSHED event when squishing entity against wall");
        assertEquals(entityId, crushedVictimId.get(), "CRUSHED event must identify victim entity ID");
        assertEquals(platId, crushedPlatformId.get(), "CRUSHED event must identify crushing platform ID");
    }

    @Test
    void testStuckBodySkipsMovement() {
        // Floor at ty=0
        for (int x = 0; x < 10; x++) grid.setTile(x, 0, TileShape.SOLID);

        // Entity embedded inside solid floor
        body.resetPosition(30, 8); // Feet at 8, inside tile y: 0..15
        int initialX = body.x;
        int initialY = body.y;

        inputState.setButtonDown(Actions.MOVE_RIGHT, true);
        tick();

        // Stuck body movement is skipped
        assertEquals(initialX, body.x, "Stuck entity must not move horizontally");
        assertEquals(initialY, body.y, "Stuck entity must not move vertically");
    }

    @Test
    void testNoVelocityInheritanceOnLeavingPlatform() {
        // Fast horizontal platform moving at 4 px/tick
        long platId = world.createEntity();
        PlatformComponent plat = new PlatformComponent(40, 16, 120, 16, 48, 16, 4.0f);
        world.setPlatform(platId, plat);
        world.applyDeferredLifecycle();

        body.resetPosition(60, 32);
        body.grounded = true;
        body.supportId = platId;

        // Rider jumps off platform
        inputState.setButtonDown(Actions.JUMP, true);
        tick();

        // Velocity must strictly be vertical jump velocity, NOT inheriting horizontal platform speed
        assertEquals(0f, body.vx, 0.001f, "Leaving a platform must not inherit platform velocity (vx must remain 0)");
        assertEquals(tuning.jumpSpeedPerTick(), body.vy, 0.001f);
        assertEquals(0L, body.supportId, "Leaving platform must clear supportId");
    }

    @Test
    void testAntiTunnelingEntityLandingOnMaxSpeedPlatform() {
        // Platform at (50, 16) with width 48, height 16 (top surface at y=32), moving at 4 px/tick
        long platId = world.createEntity();
        PlatformComponent plat = new PlatformComponent(50, 16, 120, 16, 48, 16, 4.0f);
        world.setPlatform(platId, plat);
        world.applyDeferredLifecycle();

        // Entity directly above platform top (y=32) at y=35, falling at max fall speed (6 px/tick)
        body.resetPosition(64, 35);
        body.grounded = false;
        body.vy = -tuning.maxFallSpeedPerTick();

        tick();

        // Must not tunnel through platform into y < 32; must land precisely on platform top
        assertTrue(body.grounded, "Entity falling at max speed must land on moving platform without tunneling");
        assertEquals(32, body.y, "Must land precisely on platform top at y=32");
        assertEquals(platId, body.supportId, "Must attach supportId to the moving platform");
        assertEquals(0f, body.vy, 0.001f);
    }

    @Test
    void testFastPlatformBlockedByThinWall() {
        // 1-tile-wide solid wall at tx=5 (x=80..95)
        for (int y = 0; y < 10; y++) grid.setTile(5, y, TileShape.SOLID);

        // Fast platform moving right at 6 px/tick in BLOCK mode toward wall
        long platId = world.createEntity();
        PlatformComponent plat = new PlatformComponent(30, 16, 120, 16, 32, 32, 6.0f, 0, PlatformComponent.CrushMode.BLOCK);
        world.setPlatform(platId, plat);
        world.applyDeferredLifecycle();

        // Entity between platform and wall at x=70 (right edge at 76, 4px from wall at 80)
        body.resetPosition(70, 20);

        for (int i = 0; i < 20; i++) {
            tick();
        }

        // Fast platform must not skip or tunnel past the wall; must halt cleanly
        assertTrue(plat.currentX + plat.width <= 80, "Fast BLOCK platform must halt before wall (right <= 80)");
        assertTrue(body.x + collider.getRight(0) <= 80, "Entity must not be pushed past or through the thin wall");
    }
}
