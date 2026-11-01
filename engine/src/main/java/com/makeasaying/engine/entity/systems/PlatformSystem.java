package com.makeasaying.engine.entity.systems;

import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.PlatformComponent;
import com.makeasaying.engine.entity.events.EntityEventType;
import com.makeasaying.engine.render.Snap;

/**
 * System 4 in UPDATE_ORDER: Moving platforms per AGENTS.md section 7.5 and M4 brief.
 * Updates platforms before movement, steps by integer pixels using remainders,
 * carries riders, pushes entities in path with BLOCK or CRUSH squish resolution.
 */
public final class PlatformSystem {

    public PlatformSystem() {}

    public void update(World world, TileGrid grid) {
        for (int pIdx = 1; pIdx < World.MAX_ENTITIES; pIdx++) {
            if (!world.isAliveIndex(pIdx)) continue;

            PlatformComponent plat = world.getPlatformByIndex(pIdx);
            if (plat == null) continue;

            long platformId = world.getIdByIndex(pIdx);

            // 1. Pause timer
            if (plat.pauseTimer > 0) {
                plat.pauseTimer--;
                continue;
            }

            // 2. Ping-pong trajectory step calculation
            int targetX = (plat.direction == 1) ? plat.endX : plat.startX;
            int targetY = (plat.direction == 1) ? plat.endY : plat.startY;

            float distX = targetX - plat.currentX;
            float distY = targetY - plat.currentY;
            float dist = (float) Math.sqrt(distX * distX + distY * distY);

            float stepVx;
            float stepVy;
            boolean willReachTarget = false;

            if (dist <= plat.speed || dist < 0.001f) {
                stepVx = distX;
                stepVy = distY;
                willReachTarget = true;
            } else {
                stepVx = (distX / dist) * plat.speed;
                stepVy = (distY / dist) * plat.speed;
            }

            // Remainder-based integer steps
            float totalMoveX = stepVx + plat.remainderX;
            int intMoveX = Snap.toPixel(totalMoveX);
            float remX = totalMoveX - intMoveX;

            float totalMoveY = stepVy + plat.remainderY;
            int intMoveY = Snap.toPixel(totalMoveY);
            float remY = totalMoveY - intMoveY;

            if (intMoveX == 0 && intMoveY == 0 && !willReachTarget) {
                plat.remainderX = remX;
                plat.remainderY = remY;
                continue;
            }

            // 3. Pre-check BLOCK crush condition for entities in path
            if (plat.crushMode == PlatformComponent.CrushMode.BLOCK) {
                while (intMoveX != 0 || intMoveY != 0) {
                    if (checkWouldSquish(world, grid, plat, intMoveX, intMoveY)) {
                        if (intMoveX != 0) intMoveX -= Integer.signum(intMoveX);
                        if (intMoveY != 0) intMoveY -= Integer.signum(intMoveY);
                        plat.remainderX = 0f;
                        plat.remainderY = 0f;
                    } else {
                        break;
                    }
                }
                if (intMoveX == 0 && intMoveY == 0) {
                    plat.remainderX = 0f;
                    plat.remainderY = 0f;
                    continue;
                }
            }

            // 4. Identify riders standing on top of platform before it moves
            plat.prevX = plat.currentX;
            plat.prevY = plat.currentY;

            int oldLeft = plat.currentX;
            int oldRight = plat.currentX + plat.width;
            int oldTop = plat.currentY + plat.height;

            for (int eIdx = 1; eIdx < World.MAX_ENTITIES; eIdx++) {
                if (!world.isAliveIndex(eIdx)) continue;
                BodyComponent b = world.getBodyByIndex(eIdx);
                ColliderComponent c = world.getColliderByIndex(eIdx);
                if (b == null || c == null) continue;

                // Rider check: feet at platform top and horizontally overlapping
                boolean isRider = (b.supportId == platformId) ||
                    (b.y == oldTop && b.x + c.getRight(0) > oldLeft && b.x + c.getLeft(0) < oldRight && b.grounded);

                if (isRider) {
                    b.supportId = platformId;
                    b.grounded = true;
                } else if (b.supportId == platformId) {
                    b.supportId = 0L;
                }
            }

            // 5. Move platform
            plat.currentX += intMoveX;
            plat.currentY += intMoveY;
            plat.remainderX = remX;
            plat.remainderY = remY;

            if (willReachTarget) {
                plat.currentX = targetX;
                plat.currentY = targetY;
                plat.remainderX = 0f;
                plat.remainderY = 0f;
                plat.direction = -plat.direction;
                plat.pauseTimer = plat.pauseTicks;
            }

            int newLeft = plat.currentX;
            int newRight = plat.currentX + plat.width;
            int newBottom = plat.currentY;
            int newTop = plat.currentY + plat.height;

            // 6. Carry riders and push displaced entities
            for (int eIdx = 1; eIdx < World.MAX_ENTITIES; eIdx++) {
                if (!world.isAliveIndex(eIdx)) continue;
                BodyComponent b = world.getBodyByIndex(eIdx);
                ColliderComponent c = world.getColliderByIndex(eIdx);
                if (b == null || c == null) continue;

                long entityId = world.getIdByIndex(eIdx);

                if (b.supportId == platformId) {
                    // Carry rider horizontally with wall collision
                    if (intMoveX != 0) {
                        int riderSignX = Integer.signum(intMoveX);
                        int riderStepsX = Math.abs(intMoveX);
                        for (int s = 0; s < riderStepsX; s++) {
                            int nextX = b.x + riderSignX;
                            if (MovementSystem.collidesAt(grid, c, nextX, b.y)) {
                                // Blocked by wall: rider stops against wall; platform slides beneath
                                break;
                            }
                            b.x = nextX;
                        }
                    }

                    // Carry rider vertically
                    b.y = newTop;
                    b.grounded = true;

                    // If riding pushed upward into ceiling:
                    if (MovementSystem.collidesAt(grid, c, b.x, b.y)) {
                        if (plat.crushMode == PlatformComponent.CrushMode.CRUSH) {
                            world.getEventQueue().emit(EntityEventType.CRUSHED, entityId, (int) platformId);
                        }
                    }
                } else {
                    // Entity in the way: push entity horizontally or vertically
                    int eLeft = b.x + c.getLeft(0);
                    int eRight = b.x + c.getRight(0);
                    int eBottom = b.y + c.getBottom(0);
                    int eTop = b.y + c.getTop(0);

                    boolean overlaps = (eRight > newLeft && eLeft < newRight && eTop > newBottom && eBottom < newTop);
                    if (overlaps) {
                        // Push horizontally if moving X
                        if (intMoveX > 0 && eLeft < newRight) {
                            int pushX = newRight - eLeft;
                            b.x += pushX;
                        } else if (intMoveX < 0 && eRight > newLeft) {
                            int pushX = newLeft - eRight;
                            b.x += pushX;
                        }

                        // Push vertically if moving Y
                        if (intMoveY > 0 && eBottom < newTop) {
                            int pushY = newTop - eBottom;
                            b.y += pushY;
                        } else if (intMoveY < 0 && eTop > newBottom) {
                            int pushY = newBottom - eTop;
                            b.y += pushY;
                        }

                        // Check if pushed entity overlaps solid tile
                        if (MovementSystem.collidesAt(grid, c, b.x, b.y)) {
                            if (plat.crushMode == PlatformComponent.CrushMode.CRUSH) {
                                world.getEventQueue().emit(EntityEventType.CRUSHED, entityId, (int) platformId);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean checkWouldSquish(World world, TileGrid grid, PlatformComponent plat, int moveX, int moveY) {
        int testLeft = plat.currentX + moveX;
        int testRight = testLeft + plat.width;
        int testBottom = plat.currentY + moveY;
        int testTop = testBottom + plat.height;

        for (int i = 1; i < World.MAX_ENTITIES; i++) {
            if (!world.isAliveIndex(i)) continue;
            BodyComponent b = world.getBodyByIndex(i);
            ColliderComponent c = world.getColliderByIndex(i);
            if (b == null || c == null) continue;

            int eLeft = b.x + c.getLeft(0);
            int eRight = b.x + c.getRight(0);
            int eBottom = b.y + c.getBottom(0);
            int eTop = b.y + c.getTop(0);

            // Test rider pushed upward
            if (b.supportId == world.getIdByIndex(i) || (b.y == plat.currentY + plat.height)) {
                if (moveY > 0) {
                    if (MovementSystem.collidesAt(grid, c, b.x, b.y + moveY)) {
                        return true;
                    }
                }
            }

            // Test entity pushed into wall
            boolean overlaps = (eRight > testLeft && eLeft < testRight && eTop > testBottom && eBottom < testTop);
            if (overlaps) {
                int targetX = b.x;
                int targetY = b.y;

                if (moveX > 0) targetX += (testRight - eLeft);
                else if (moveX < 0) targetX += (testLeft - eRight);

                if (moveY > 0) targetY += (testTop - eBottom);
                else if (moveY < 0) targetY += (testBottom - eTop);

                if (MovementSystem.collidesAt(grid, c, targetX, targetY)) {
                    return true;
                }
            }
        }
        return false;
    }
}
