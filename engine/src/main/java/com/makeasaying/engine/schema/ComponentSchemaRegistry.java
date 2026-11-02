package com.makeasaying.engine.schema;

import com.makeasaying.engine.entity.ControlSource;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.ControllerComponent;
import com.makeasaying.engine.entity.components.FacingComponent;
import com.makeasaying.engine.entity.components.JumpStateComponent;
import com.makeasaying.engine.entity.components.PlatformComponent;
import com.makeasaying.engine.entity.components.RoomScopeComponent;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, instantiated registry of component schemas per AGENTS.md M6b brief.
 * Single source of truth driving:
 * 1. Data file validation (EntityDefinitionLoader, RoomValidator)
 * 2. Runtime entity spawning (EntitySpawner)
 * 3. In-game editor inspector (InspectorModel)
 * Built as an instance passed in at startup (no static singleton).
 */
public final class ComponentSchemaRegistry {
    private final Map<String, ComponentSchema> schemas = new LinkedHashMap<>();

    public ComponentSchemaRegistry() {}

    public void register(ComponentSchema schema) {
        if (schema == null) throw new IllegalArgumentException("Schema cannot be null");
        schemas.put(schema.id(), schema);
    }

    public ComponentSchema getSchema(String id) {
        return schemas.get(id);
    }

    public boolean hasSchema(String id) {
        return schemas.containsKey(id);
    }

    public Collection<ComponentSchema> getAllSchemas() {
        return Collections.unmodifiableCollection(schemas.values());
    }

    /**
     * Factory creating the default engine schema registry populated with all standard components.
     */
    public static ComponentSchemaRegistry createDefault() {
        ComponentSchemaRegistry registry = new ComponentSchemaRegistry();

        // 1. Body (spawn position only)
        registry.register(new ComponentSchema(
            "body",
            List.of(
                ComponentField.integerField("x", 0, -100000, 100000, "Spawn X position in integer pixels", false),
                ComponentField.integerField("y", 0, -100000, 100000, "Spawn Y position in integer pixels", false)
            ),
            (world, entityId, values) -> {
                int x = ((Number) values.getOrDefault("x", 0)).intValue();
                int y = ((Number) values.getOrDefault("y", 0)).intValue();
                BodyComponent body = world.getBody(entityId);
                if (body == null) {
                    body = new BodyComponent(x, y);
                    world.setBody(entityId, body);
                } else {
                    body.x = x;
                    body.y = y;
                    body.prevX = x;
                    body.prevY = y;
                    body.subpixelX = 0f;
                    body.subpixelY = 0f;
                }
            },
            (world, entityId) -> {
                BodyComponent body = world.getBody(entityId);
                if (body == null) return Collections.emptyMap();
                return Map.of("x", body.x, "y", body.y);
            }
        ));

        // 2. Collider
        registry.register(new ComponentSchema(
            "collider",
            List.of(
                ComponentField.integerField("width", 16, 1, 4096, "Body collider width in pixels", true),
                ComponentField.integerField("height", 16, 1, 4096, "Body collider height in pixels", true),
                ComponentField.integerField("offsetX", 0, -1024, 1024, "Collider horizontal offset from feet anchor", true),
                ComponentField.integerField("offsetY", 0, -1024, 1024, "Collider vertical offset from feet anchor", true)
            ),
            (world, entityId, values) -> {
                int w = ((Number) values.getOrDefault("width", 16)).intValue();
                int h = ((Number) values.getOrDefault("height", 16)).intValue();
                int ox = ((Number) values.getOrDefault("offsetX", 0)).intValue();
                int oy = ((Number) values.getOrDefault("offsetY", 0)).intValue();
                ColliderComponent col = world.getCollider(entityId);
                if (col == null) {
                    world.setCollider(entityId, new ColliderComponent(w, h, ox, oy));
                } else {
                    col.width = w;
                    col.height = h;
                    col.offsetX = ox;
                    col.offsetY = oy;
                }
            },
            (world, entityId) -> {
                ColliderComponent col = world.getCollider(entityId);
                if (col == null) return Collections.emptyMap();
                return Map.of("width", col.width, "height", col.height, "offsetX", col.offsetX, "offsetY", col.offsetY);
            }
        ));

        // 3. Facing
        registry.register(new ComponentSchema(
            "facing",
            List.of(
                ComponentField.booleanField("facingRight", true, "Initial horizontal facing direction", true)
            ),
            (world, entityId, values) -> {
                boolean right = Boolean.TRUE.equals(values.getOrDefault("facingRight", true));
                FacingComponent f = world.getFacing(entityId);
                if (f == null) {
                    world.setFacing(entityId, new FacingComponent(right ? 1 : -1));
                } else {
                    f.direction = right ? 1 : -1;
                }
            },
            (world, entityId) -> {
                FacingComponent f = world.getFacing(entityId);
                if (f == null) return Collections.emptyMap();
                return Map.of("facingRight", f.direction >= 0);
            }
        ));

        // 4. Controller
        registry.register(new ComponentSchema(
            "controller",
            List.of(
                ComponentField.enumField(
                    "source",
                    "NONE",
                    List.of("PLAYER", "AI", "SCRIPT", "CUTSCENE_SIM", "NONE"),
                    "Control authority source for entity movement",
                    true
                )
            ),
            (world, entityId, values) -> {
                String srcStr = String.valueOf(values.getOrDefault("source", "NONE"));
                ControlSource src = ControlSource.valueOf(srcStr);
                ControllerComponent c = world.getController(entityId);
                if (c == null) {
                    world.setController(entityId, new ControllerComponent(src));
                } else {
                    c.source = src;
                }
            },
            (world, entityId) -> {
                ControllerComponent c = world.getController(entityId);
                if (c == null) return Collections.emptyMap();
                return Map.of("source", c.source.name());
            }
        ));

        // 5. RoomScope
        registry.register(new ComponentSchema(
            "room_scope",
            List.of(
                ComponentField.enumField(
                    "scope",
                    "ROOM_LOCAL",
                    List.of("PERSISTENT_CARRIED", "ROOM_LOCAL"),
                    "Entity lifetime scope across room transitions",
                    false
                )
            ),
            (world, entityId, values) -> {
                String scStr = String.valueOf(values.getOrDefault("scope", "ROOM_LOCAL"));
                RoomScopeComponent.Scope sc = RoomScopeComponent.Scope.valueOf(scStr);
                RoomScopeComponent r = world.getRoomScope(entityId);
                if (r == null) {
                    world.setRoomScope(entityId, new RoomScopeComponent(sc));
                } else {
                    r.scope = sc;
                }
            },
            (world, entityId) -> {
                RoomScopeComponent r = world.getRoomScope(entityId);
                if (r == null) return Collections.emptyMap();
                return Map.of("scope", r.scope.name());
            }
        ));

        // 6. JumpState
        registry.register(new ComponentSchema(
            "jump_state",
            List.of(
                ComponentField.integerField("coyoteTicks", 0, 0, 60, "Remaining coyote timer ticks", false),
                ComponentField.integerField("jumpBufferTicks", 0, 0, 60, "Remaining jump buffer timer ticks", false)
            ),
            (world, entityId, values) -> {
                JumpStateComponent js = world.getJumpState(entityId);
                if (js == null) {
                    js = new JumpStateComponent();
                    world.setJumpState(entityId, js);
                }
                js.coyoteTimer = ((Number) values.getOrDefault("coyoteTicks", 0)).intValue();
                js.jumpBufferTimer = ((Number) values.getOrDefault("jumpBufferTicks", 0)).intValue();
            },
            (world, entityId) -> {
                JumpStateComponent js = world.getJumpState(entityId);
                if (js == null) return Collections.emptyMap();
                return Map.of("coyoteTicks", js.coyoteTimer, "jumpBufferTicks", js.jumpBufferTimer);
            }
        ));

        // 7. Platform
        registry.register(new ComponentSchema(
            "platform",
            List.of(
                ComponentField.integerField("travelX", 0, -10000, 10000, "Horizontal travel pixel offset from spawn point", true),
                ComponentField.integerField("travelY", 0, -10000, 10000, "Vertical travel pixel offset from spawn point", true),
                ComponentField.floatField("speed", 32.0f, 0.1f, 1000.0f, "Authored travel speed in pixels per second", true),
                ComponentField.integerField("pauseTicks", 0, 0, 600, "Pause ticks at each turnaround endpoint", true),
                ComponentField.enumField("crushMode", "BLOCK", List.of("BLOCK", "CRUSH"), "Squish response when sandwiching rider against solid terrain", true)
            ),
            (world, entityId, values) -> {
                BodyComponent body = world.getBody(entityId);
                int sx = body != null ? body.x : 0;
                int sy = body != null ? body.y : 0;
                int tx = ((Number) values.getOrDefault("travelX", 0)).intValue();
                int ty = ((Number) values.getOrDefault("travelY", 0)).intValue();
                float spdPxS = ((Number) values.getOrDefault("speed", 32.0f)).floatValue();
                int pt = ((Number) values.getOrDefault("pauseTicks", 0)).intValue();
                String cmStr = String.valueOf(values.getOrDefault("crushMode", "BLOCK"));
                PlatformComponent.CrushMode cm = PlatformComponent.CrushMode.valueOf(cmStr);

                ColliderComponent col = world.getCollider(entityId);
                int pw = col != null ? col.width : 48;
                int ph = col != null ? col.height : 16;

                // Convert authored speed (px/s) once at spawn to speed per tick (60 Hz) per DEC-010 & DEC-025
                float speedPerTick = spdPxS / 60.0f;

                PlatformComponent plat = world.getPlatform(entityId);
                if (plat == null) {
                    plat = new PlatformComponent(sx, sy, sx + tx, sy + ty, pw, ph, speedPerTick, pt, cm);
                    world.setPlatform(entityId, plat);
                } else {
                    plat.startX = sx;
                    plat.startY = sy;
                    plat.endX = sx + tx;
                    plat.endY = sy + ty;
                    plat.currentX = sx;
                    plat.currentY = sy;
                    plat.prevX = sx;
                    plat.prevY = sy;
                    plat.width = pw;
                    plat.height = ph;
                    plat.speed = speedPerTick;
                    plat.pauseTicks = pt;
                    plat.crushMode = cm;
                }
            },
            (world, entityId) -> {
                PlatformComponent plat = world.getPlatform(entityId);
                if (plat == null) return Collections.emptyMap();
                int travelX = plat.endX - plat.startX;
                int travelY = plat.endY - plat.startY;
                float authoredSpeed = plat.speed * 60.0f;
                return Map.of(
                    "travelX", travelX,
                    "travelY", travelY,
                    "speed", authoredSpeed,
                    "pauseTicks", plat.pauseTicks,
                    "crushMode", plat.crushMode.name()
                );
            }
        ));

        // 8. Savepoint
        registry.register(new ComponentSchema(
            "savepoint",
            Collections.emptyList(),
            (world, entityId, values) -> {
                com.makeasaying.engine.entity.components.SavepointComponent sp = world.getSavepoint(entityId);
                if (sp == null) {
                    world.setSavepoint(entityId, new com.makeasaying.engine.entity.components.SavepointComponent(true));
                }
            },
            (world, entityId) -> {
                com.makeasaying.engine.entity.components.SavepointComponent sp = world.getSavepoint(entityId);
                return sp != null ? Map.of("active", sp.active) : Collections.emptyMap();
            }
        ));

        return registry;
    }
}
