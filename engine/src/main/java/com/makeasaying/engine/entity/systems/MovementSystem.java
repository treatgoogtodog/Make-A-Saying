package com.makeasaying.engine.entity.systems;

import com.makeasaying.engine.entity.MovementTuning;
import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.ControllerComponent;
import com.makeasaying.engine.entity.components.FacingComponent;
import com.makeasaying.engine.entity.components.InputStateComponent;
import com.makeasaying.engine.entity.components.JumpStateComponent;
import com.makeasaying.engine.entity.events.EntityEventType;
import com.makeasaying.engine.input.Actions;
import com.makeasaying.engine.input.InputState;
import com.makeasaying.engine.render.Snap;

/**
 * System 2 in M3: Movement physics and tile grid collision per AGENTS.md section 7.5.
 * Steps one axis at a time (X then Y), integer pixel steps with subpixel remainders,
 * ceiling corner correction, and ground probe. Zero-allocation.
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

            FacingComponent facing = world.getFacingByIndex(i);
            InputStateComponent inputComp = world.getInputStateByIndex(i);
            JumpStateComponent jumpState = world.getJumpStateByIndex(i);
            InputState input = (inputComp != null) ? inputComp.state : null;
            long entityId = world.getIdByIndex(i);

            // 1. Process Input
            int moveX = (input != null) ? input.getAxis(Actions.MOVE_X) : 0;
            boolean jumpPressed = (input != null) && input.isPressed(Actions.JUMP);
            boolean jumpDown = (input != null) && input.isDown(Actions.JUMP);

            if (facing != null && moveX != 0) {
                facing.direction = moveX;
            }

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
                if (collidesAt(grid, collider, nextX, body.y)) {
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
                if (collidesAt(grid, collider, body.x, nextY)) {
                    if (signY > 0) {
                        // Moving UP: Attempt ceiling corner correction
                        int correctedX = tryCeilingCornerCorrection(grid, collider, body.x, nextY, tuning.cornerCorrectionPx());
                        if (correctedX != body.x) {
                            body.x = correctedX;
                            body.y = nextY; // Corner corrected! Pass through ceiling notch
                            continue;
                        }

                        // Bonked ceiling
                        body.vy = 0f;
                        body.subpixelY = 0f;
                        world.getEventQueue().emit(EntityEventType.HIT_CEILING, entityId, 1);
                    } else {
                        // Landed on floor
                        body.vy = 0f;
                        body.subpixelY = 0f;
                        if (jumpState != null && jumpState.jumpBufferTimer > 0) {
                            body.vy = tuning.jumpSpeedPerTick();
                            jumpState.jumpBufferTimer = 0;
                            jumpState.coyoteTimer = 0;
                            jumpState.hasJumpCut = false;
                            body.grounded = false;
                        }
                    }
                    break;
                }
                body.y = nextY;
            }

            // 6. Grounded Check (probe 1px below feet)
            boolean isNowGrounded = collidesAt(grid, collider, body.x, body.y - 1);
            if (body.vy > 0f) {
                isNowGrounded = false;
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
                    return true;
                }
            }
        }
        return false;
    }

    private static int tryCeilingCornerCorrection(TileGrid grid, ColliderComponent collider, int feetX, int feetY, int maxShift) {
        // Try shifting right by 1..maxShift
        for (int shift = 1; shift <= maxShift; shift++) {
            if (!collidesAt(grid, collider, feetX + shift, feetY)) {
                return feetX + shift;
            }
        }
        // Try shifting left by 1..maxShift
        for (int shift = 1; shift <= maxShift; shift++) {
            if (!collidesAt(grid, collider, feetX - shift, feetY)) {
                return feetX - shift;
            }
        }
        return feetX; // No clearance
    }
}
