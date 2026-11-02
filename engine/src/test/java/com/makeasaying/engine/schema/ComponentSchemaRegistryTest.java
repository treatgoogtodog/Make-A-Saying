package com.makeasaying.engine.schema;

import com.makeasaying.engine.entity.World;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ComponentSchemaRegistryTest {

    @Test
    void testDefaultRegistryHasRequiredComponents() {
        ComponentSchemaRegistry registry = ComponentSchemaRegistry.createDefault();
        assertTrue(registry.hasSchema("body"));
        assertTrue(registry.hasSchema("collider"));
        assertTrue(registry.hasSchema("facing"));
        assertTrue(registry.hasSchema("controller"));
        assertTrue(registry.hasSchema("room_scope"));
        assertTrue(registry.hasSchema("jump_state"));
        assertTrue(registry.hasSchema("platform"));

        ComponentSchema platSchema = registry.getSchema("platform");
        assertTrue(platSchema.hasField("travelX"));
        assertTrue(platSchema.hasField("travelY"));
        assertTrue(platSchema.hasField("speed"));
        assertTrue(platSchema.hasField("pauseTicks"));
        assertTrue(platSchema.hasField("crushMode"));
    }

    @Test
    void testApplyAndReadPlatformParity() {
        ComponentSchemaRegistry registry = ComponentSchemaRegistry.createDefault();
        World world = new World();
        long entity = world.createEntity();

        // 1. Apply body and collider first
        registry.getSchema("body").apply(world, entity, Map.of("x", 100, "y", 50));
        registry.getSchema("collider").apply(world, entity, Map.of("width", 48, "height", 16));

        // 2. Apply platform with human units: travelX = 120, speed = 60 px/s (1 px/tick at 60 Hz)
        registry.getSchema("platform").apply(world, entity, Map.of(
            "travelX", 120,
            "travelY", 0,
            "speed", 60.0f,
            "pauseTicks", 15,
            "crushMode", "CRUSH"
        ));

        // 3. Verify internal component values
        var plat = world.getPlatform(entity);
        assertNotNull(plat);
        assertEquals(100, plat.startX);
        assertEquals(220, plat.endX);
        assertEquals(1.0f, plat.speed, 0.0001f); // 60 px/s / 60 Hz = 1.0 px/tick
        assertEquals(15, plat.pauseTicks);
        assertEquals(com.makeasaying.engine.entity.components.PlatformComponent.CrushMode.CRUSH, plat.crushMode);

        // 4. Read back and verify exact values
        Map<String, Object> read = registry.getSchema("platform").read(world, entity);
        assertEquals(120, read.get("travelX"));
        assertEquals(0, read.get("travelY"));
        assertEquals(60.0f, (Float) read.get("speed"), 0.0001f);
        assertEquals(15, read.get("pauseTicks"));
        assertEquals("CRUSH", read.get("crushMode"));
    }

    @Test
    void testSyntheticComponentSingleSourceRegistration() {
        // Pure synthetic instance without modifying default or global state
        ComponentSchemaRegistry customRegistry = new ComponentSchemaRegistry();
        assertFalse(customRegistry.hasSchema("synthetic_battery"));

        int[] batteryStorage = new int[16];
        float[] voltageStorage = new float[16];

        ComponentSchema batterySchema = new ComponentSchema(
            "synthetic_battery",
            List.of(
                ComponentField.integerField("chargePercent", 100, 0, 100, "Remaining charge percentage", true),
                ComponentField.floatField("voltage", 3.7f, 0.0f, 12.0f, "Current cell voltage", true)
            ),
            (w, id, values) -> {
                int slot = (int) (id & 0x0F);
                batteryStorage[slot] = ((Number) values.getOrDefault("chargePercent", 100)).intValue();
                voltageStorage[slot] = ((Number) values.getOrDefault("voltage", 3.7f)).floatValue();
            },
            (w, id) -> {
                int slot = (int) (id & 0x0F);
                return Map.of("chargePercent", batteryStorage[slot], "voltage", voltageStorage[slot]);
            }
        );

        customRegistry.register(batterySchema);
        assertTrue(customRegistry.hasSchema("synthetic_battery"));

        World world = new World();
        long entityId = 5L;
        batterySchema.apply(world, entityId, Map.of("chargePercent", 82, "voltage", 3.95f));

        Map<String, Object> read = batterySchema.read(world, entityId);
        assertEquals(82, read.get("chargePercent"));
        assertEquals(3.95f, (Float) read.get("voltage"), 0.001f);
    }
}
