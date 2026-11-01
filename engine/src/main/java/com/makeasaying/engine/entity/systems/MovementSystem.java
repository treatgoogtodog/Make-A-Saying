package com.makeasaying.engine.entity.systems;

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
import com.makeasaying.engine.input.Actions;
import com.makeasaying.engine.input.InputState;
import com.makeasaying.engine.render.Snap;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

/**
 * System 5 in UPDATE_ORDER: Movement physics, tile collision, slopes, and one-way platforms
 * per AGENTS.md section 7.5 and M4 brief.
 * Steps one axis at a time, integer pixel stepping with subpixel remainders,
 * two-column slope sampling, slope surface snapping, one-way drop-through,
 * ceiling corner correction, and stuck-body detection. Zero-allocation.
 */
public final class MovementSystem {
    private MovementTuning tuning;

    public MovementSystem(MovementTuning tuning) {
        setTuning(tuning);
    }

    public void setTuning(MovementTuning tuning) {
        if (tuning == null) throw new IllegalArgumentException("Tuning cannot be null");
        this.tuning = tuning;
    }

    public MovementTuning getTuning() {
        return tuning;
    }

    public void update(World world, TileGrid grid) {
        for (int i = 1; i < World.MAX_ENTITIES; i++) {
            if (!world.isAliveIndex(i)) continue;

            BodyComponent body = world.getBodyByIndex(i);
            ColliderComponent collider = world.getColliderByIndex(i);
            if (body == null || collider == null) continue;

            ControllerComponent controller = world.getControllerByIndex(i);
            if (controller != null && controller.puppet) {
                continue; // Puppet skips movement
            }

            long entityId = world.getIdByIndex(i);

            // 0. Stuck-body check: if starting tick overlapping solid, skip movement and log warning
            if (isSolidOrPlatform(world, grid, collider, body.x, body.y)) {
                Log.warn(LogCategory.PHYSICS, "Entity %d stuck inside solid at (%d, %d)! Skipping movement.",
                    entityId, body.x, body.y);
                continue;
            }

            FacingComponent facing = world.getFacingByIndex(i);
            InputStateComponent inputComp = world.getInputStateByIndex(i);
            JumpStateComponent jumpState = world.getJumpStateByIndex(i);
            InputState input = (inputComp != null) ? inputComp.state : null;

            // 1. Process Input
            int moveX = (input != null) ? input.getAxis(Actions.MOVE_X) : 0;
            int moveY = (input != null) ? input.getAxis(Actions.MOVE_Y) : 0;
            boolean jumpPressed = (input != null) && input.isPressed(Actions.JUMP);
            boolean jumpDown = (input != null) && input.isDown(Actions.JUMP);

            if (facing != null && moveX != 0) {
                facing.direction = moveX;
            }

            // Drop-through on ONE_WAY platform (down + jump)
            if (body.grounded && jumpState != null && moveY < 0 && jumpPressed) {
                if (isGroundedOnOneWay(grid, body.x, body.y)) {
                    jumpState.dropThroughTimer = tuning.dropThroughTicks();
                    body.grounded = false;
                    body.y = body.y - 1; // Drop feet into/below one-way platform surface
                    body.supportId = 0L;
                    jumpPressed = false; // Suppress jump trigger
                    jumpState.jumpBufferTimer = 0; // Suppress jump buffer
                    jumpState.coyoteTimer = 0;
                }
            }

            if (jumpState != null && jumpState.dropThroughTimer > 0) {
                jumpState.dropThroughTimer--;
            }

            boolean ignoreOneWay = (jumpState != null && jumpState.dropThroughTimer > 0);

            // 2. Horizontal Acceleration & Friction
            float targetVx = moveX * tuning.maxRunSpeedPerTick();
            if (body.grounded) {
                if (moveX == 0) {
                    body.vx = approach(body.vx, 0f, tuning.groundFrictionPerTick());
                } else if ((body.vx > 0f && moveX < 0) || (body.vx < 0f && moveX > 0)) {
                    body.vx = approach(body.vx, targetVx, tuning.groundTurnaroundPerTick());
                } else {
                    body.vx = approach(body.vx, targetVx, tuning.groundAccelPerTick());
                }
            } else {
                if (moveX == 0) {
                    body.vx = approach(body.vx, 0f, tuning.airFrictionPerTick());
                } else if ((body.vx > 0f && moveX < 0) || (body.vx < 0f && moveX > 0)) {
                    body.vx = approach(body.vx, targetVx, tuning.airTurnaroundPerTick());
                } else {
                    body.vx = approach(body.vx, targetVx, tuning.airAccelPerTick());
                }
            }

            // 3. Vertical Gravity
            if (!body.grounded) {
                float grav = (body.vy > 0f) ? tuning.riseGravityPerTick() : tuning.fallGravityPerTick();
                body.vy -= grav;
                if (body.vy < -tuning.maxFallSpeedPerTick()) {
                    body.vy = -tuning.maxFallSpeedPerTick();
                }
            }

            // 4. Jump Timers & Trigger
            boolean jumpTriggeredThisTick = false;
            if (jumpState != null) {
                if (body.grounded) {
                    jumpState.coyoteTimer = tuning.coyoteTicks();
                    jumpState.hasJumpCut = false;
                } else if (jumpState.coyoteTimer > 0) {
                    jumpState.coyoteTimer--;
                }

                if (jumpPressed) {
                    jumpState.jumpBufferTimer = tuning.jumpBufferTicks();
                } else if (jumpState.jumpBufferTimer > 0) {
                    jumpState.jumpBufferTimer--;
                }

                if (jumpState.jumpBufferTimer > 0 && jumpState.coyoteTimer > 0) {
                    body.vy = tuning.jumpSpeedPerTick();
                    jumpState.coyoteTimer = 0; // Prevent double jump
                    jumpState.jumpBufferTimer = 0;
                    jumpState.hasJumpCut = false;
                    body.grounded = false;
                    body.supportId = 0L;
                    jumpTriggeredThisTick = true;
                }

                // Variable jump height: early release cut
                if (!jumpDown && body.vy > 0f && !jumpState.hasJumpCut) {
                    body.vy *= tuning.jumpCutMultiplier();
                    jumpState.hasJumpCut = true;
                }
            }

            // 5. Stepping with Integer Pixels & Symmetric Remainder (DEC-009)
            body.prevX = body.x;
            body.prevY = body.y;

            // --- Step Horizontal (X) ---
            float totalMoveX = body.vx + body.subpixelX;
            int intMoveX = Snap.toPixel(totalMoveX);
            body.subpixelX = totalMoveX - intMoveX;

            int signX = Integer.signum(intMoveX);
            int remainingStepsX = Math.abs(intMoveX);

            for (int s = 0; s < remainingStepsX; s++) {
                int nextX = body.x + signX;

                // Grounded slope walking: check floor surface at nextX
                if (body.grounded && body.vy <= 0f && !jumpTriggeredThisTick) {
                    int newFloor = getFloorSurface(world, grid, nextX, body.y, tuning.slopeSnapPx(), 16, ignoreOneWay);
                    if (newFloor != Integer.MIN_VALUE) {
                        if (newFloor >= body.y) {
                            // Rising slope or stepping up onto flat floor
                            if (!isSolidOrPlatform(world, grid, collider, nextX, newFloor)) {
                                body.x = nextX;
                                body.y = newFloor;
                                continue;
                            }
                        } else {
                            // Descending slope
                            int drop = body.y - newFloor;
                            if (drop <= tuning.slopeSnapPx()) {
                                body.x = nextX;
                                body.y = newFloor;
                                continue;
                            } else {
                                // Walked off into air: no launch (vy stays 0)
                                if (!isSolidOrPlatform(world, grid, collider, nextX, body.y)) {
                                    body.x = nextX;
                                    body.grounded = false;
                                    body.vy = 0f;
                                    body.supportId = 0L;
                                    continue;
                                }
                            }
                        }
                    }
                }

                // Normal flat / airborne move: check only solid tiles and moving platforms
                if (isSolidOrPlatform(world, grid, collider, nextX, body.y)) {
                    body.vx = 0f;
                    body.subpixelX = 0f;
                    world.getEventQueue().emit(EntityEventType.HIT_WALL, entityId, signX);
                    break;
                }
                body.x = nextX;
            }

            // --- Step Vertical (Y) ---
            float totalMoveY = body.vy + body.subpixelY;
            int intMoveY = Snap.toPixel(totalMoveY);
            body.subpixelY = totalMoveY - intMoveY;

            int signY = Integer.signum(intMoveY);
            int remainingStepsY = Math.abs(intMoveY);

            for (int s = 0; s < remainingStepsY; s++) {
                int nextY = body.y + signY;

                if (signY > 0) {
                    // Moving UP: slopes and one-way never block; only solid and platforms block
                    if (isSolidOrPlatform(world, grid, collider, body.x, nextY)) {
                        int correctedX = tryCeilingCornerCorrection(world, grid, collider, body.x, nextY, tuning.cornerCorrectionPx());
                        if (correctedX != body.x) {
                            body.x = correctedX;
                            body.y = nextY;
                            continue;
                        }

                        // Bonked ceiling
                        body.vy = 0f;
                        body.subpixelY = 0f;
                        world.getEventQueue().emit(EntityEventType.HIT_CEILING, entityId, 1);
                        break;
                    }
                    body.y = nextY;
                } else if (signY < 0) {
                    // Moving DOWN: check solid tiles, platforms, slopes, and one-way
                    boolean hitSolid = isSolidOrPlatform(world, grid, collider, body.x, nextY);
                    int searchWindow = Math.abs(intMoveY) + 2;
                    int surfaceY = getFloorSurface(world, grid, body.x, body.y, searchWindow, 0, ignoreOneWay);
                    boolean hitSurface = (surfaceY != Integer.MIN_VALUE && nextY <= surfaceY && body.prevY >= surfaceY);

                    if (hitSolid || hitSurface) {
                        if (hitSurface) {
                            body.y = surfaceY;
                        }
                        body.vy = 0f;
                        body.subpixelY = 0f;

                        // Buffered jump trigger upon landing
                        if (jumpState != null && jumpState.jumpBufferTimer > 0) {
                            body.vy = tuning.jumpSpeedPerTick();
                            jumpState.jumpBufferTimer = 0;
                            jumpState.coyoteTimer = 0;
                            jumpState.hasJumpCut = false;
                            body.grounded = false;
                            body.supportId = 0L;
                        }
                        break;
                    }
                    body.y = nextY;
                }
            }

            // 6. Grounded Check & Support Platform Resolution
            int floorSurfaceBelow = getFloorSurface(world, grid, body.x, body.y, 1, 0, ignoreOneWay);
            boolean onSolidBelow = isSolidOrPlatform(world, grid, collider, body.x, body.y - 1);
            boolean isNowGrounded = (floorSurfaceBelow == body.y) || onSolidBelow;

            if (body.vy > 0f) {
                isNowGrounded = false;
            }

            // Resolve supportId
            if (isNowGrounded) {
                body.supportId = findPlatformUnderFeet(world, body.x, body.y);
            } else {
                body.supportId = 0L;
            }

            body.wasGrounded = body.grounded;
            body.grounded = isNowGrounded;

            // Emit LANDED event strictly once upon touchdown
            if (isNowGrounded && !body.wasGrounded) {
                world.getEventQueue().emit(EntityEventType.LANDED, entityId, 0);
            }
        }
    }

    private static float approach(float current, float target, float step) {
        if (current < target) {
            return Math.min(current + step, target);
        } else {
            return Math.max(current - step, target);
        }
    }

    /**
     * Checks if collider at (feetX, feetY) overlaps a full SOLID tile in the grid.
     */
    public static boolean collidesAt(TileGrid grid, ColliderComponent collider, int feetX, int feetY) {
        int left = collider.getLeft(feetX);
        int right = collider.getRight(feetX);
        int bottom = collider.getBottom(feetY);
        int top = collider.getTop(feetY);

        int tileSize = grid.getTileSize();
        int tileLeft = Math.floorDiv(left, tileSize);
        int tileRight = Math.floorDiv(right - 1, tileSize);
        int tileBottom = Math.floorDiv(bottom, tileSize);
        int tileTop = Math.floorDiv(top - 1, tileSize);

        for (int ty = tileBottom; ty <= tileTop; ty++) {
            for (int tx = tileLeft; tx <= tileRight; tx++) {
                if (grid.isSolid(tx, ty)) {
                    if (isSeamedSlopeFloor(grid, tx, ty, feetX, feetY)) {
                        continue;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSeamedSlopeFloor(TileGrid grid, int tx, int ty, int feetX, int feetY) {
        int tileSize = grid.getTileSize();
        int tileTopY = (ty + 1) * tileSize;
        if (feetY > tileTopY) return false;

        // Approaching solid tile from the left where left neighbor is SLOPE_45_UP_RIGHT rising to this tile's top
        if (feetX < tx * tileSize) {
            if (grid.getShape(tx - 1, ty) == TileShape.SLOPE_45_UP_RIGHT) {
                return true;
            }
        }

        // Approaching solid tile from the right where right neighbor is SLOPE_45_UP_LEFT rising to this tile's top
        if (feetX >= (tx + 1) * tileSize) {
            if (grid.getShape(tx + 1, ty) == TileShape.SLOPE_45_UP_LEFT) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if collider at (feetX, feetY) overlaps a full SOLID tile or any moving platform.
     */
    public static boolean isSolidOrPlatform(World world, TileGrid grid, ColliderComponent collider, int feetX, int feetY) {
        if (collidesAt(grid, collider, feetX, feetY)) {
            return true;
        }

        if (world != null) {
            int left = collider.getLeft(feetX);
            int right = collider.getRight(feetX);
            int bottom = collider.getBottom(feetY);
            int top = collider.getTop(feetY);

            for (int i = 1; i < World.MAX_ENTITIES; i++) {
                if (!world.isAliveIndex(i)) continue;
                PlatformComponent p = world.getPlatformByIndex(i);
                if (p == null) continue;

                int pLeft = p.currentX;
                int pRight = p.currentX + p.width;
                int pBottom = p.currentY;
                int pTop = p.currentY + p.height;

                if (right > pLeft && left < pRight && top > pBottom && bottom < pTop) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Finds highest floor surface at the two middle columns of the 12px collider (feetX - 1 and feetX).
     */
    public static int getFloorSurface(World world, TileGrid grid, int feetX, int feetY, int searchDownPx, int searchUpPx, boolean ignoreOneWay) {
        int col1 = feetX - 1;
        int col2 = feetX;

        int surf1 = grid.getFloorSurfaceAtCol(col1, feetY, searchDownPx, searchUpPx, ignoreOneWay);
        int surf2 = grid.getFloorSurfaceAtCol(col2, feetY, searchDownPx, searchUpPx, ignoreOneWay);
        int tileFloor = Math.max(surf1, surf2);

        int platFloor = Integer.MIN_VALUE;
        if (world != null) {
            for (int p = 1; p < World.MAX_ENTITIES; p++) {
                if (!world.isAliveIndex(p)) continue;
                PlatformComponent plat = world.getPlatformByIndex(p);
                if (plat == null) continue;

                int pLeft = plat.currentX;
                int pRight = plat.currentX + plat.width;
                int pTop = plat.currentY + plat.height;

                if ((col1 >= pLeft && col1 < pRight) || (col2 >= pLeft && col2 < pRight)) {
                    if (pTop <= feetY + searchUpPx && pTop >= feetY - searchDownPx) {
                        if (pTop > platFloor) {
                            platFloor = pTop;
                        }
                    }
                }
            }
        }

        return Math.max(tileFloor, platFloor);
    }

    private static boolean isGroundedOnOneWay(TileGrid grid, int feetX, int feetY) {
        int col1 = feetX - 1;
        int col2 = feetX;
        int tileSize = grid.getTileSize();
        int tileY = Math.floorDiv(feetY - 1, tileSize);

        int tx1 = Math.floorDiv(col1, tileSize);
        int tx2 = Math.floorDiv(col2, tileSize);

        return (grid.getShape(tx1, tileY) == TileShape.ONE_WAY && (tileY + 1) * tileSize == feetY) ||
               (grid.getShape(tx2, tileY) == TileShape.ONE_WAY && (tileY + 1) * tileSize == feetY);
    }

    private static long findPlatformUnderFeet(World world, int feetX, int feetY) {
        if (world == null) return 0L;
        int col1 = feetX - 1;
        int col2 = feetX;

        for (int p = 1; p < World.MAX_ENTITIES; p++) {
            if (!world.isAliveIndex(p)) continue;
            PlatformComponent plat = world.getPlatformByIndex(p);
            if (plat == null) continue;

            int pLeft = plat.currentX;
            int pRight = plat.currentX + plat.width;
            int pTop = plat.currentY + plat.height;

            if (pTop == feetY && ((col1 >= pLeft && col1 < pRight) || (col2 >= pLeft && col2 < pRight))) {
                return world.getIdByIndex(p);
            }
        }
        return 0L;
    }

    private static int tryCeilingCornerCorrection(World world, TileGrid grid, ColliderComponent collider, int feetX, int feetY, int maxShift) {
        // Corner correction ignores one-way tiles and slopes; only avoids solid and moving platforms
        for (int shift = 1; shift <= maxShift; shift++) {
            if (!isSolidOrPlatform(world, grid, collider, feetX + shift, feetY)) {
                return feetX + shift;
            }
        }
        for (int shift = 1; shift <= maxShift; shift++) {
            if (!isSolidOrPlatform(world, grid, collider, feetX - shift, feetY)) {
                return feetX - shift;
            }
        }
        return feetX;
    }
}
