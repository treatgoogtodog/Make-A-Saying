package com.makeasaying.editor.map.model;

import com.makeasaying.engine.entity.EntityDefinition;
import com.makeasaying.engine.schema.ComponentSchemaRegistry;
import com.makeasaying.engine.world.RoomEntityData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PureEditorModelsTest {

    @Test
    void testEntityPaletteModelFiltersNonPlaceableAndSearches() {
        EntityDefinition def1 = new EntityDefinition(1, "player", List.of("actor"), "#FFFFFF", false, Map.of());
        EntityDefinition def2 = new EntityDefinition(1, "moving_platform", List.of("platform", "hazard"), "#4488FF", true, Map.of());
        EntityDefinition def3 = new EntityDefinition(1, "door_switch", List.of("prop"), "#FFCC00", true, Map.of());

        EntityPaletteModel palette = new EntityPaletteModel(Map.of("player", def1, "moving_platform", def2, "door_switch", def3));

        // Player is placeable: false, so should be filtered out
        assertEquals(2, palette.getFilteredDefinitions().size());
        assertTrue(palette.getFilteredDefinitions().stream().noneMatch(d -> d.id().equals("player")));

        // Search query
        palette.setSearchQuery("hazard");
        assertEquals(1, palette.getFilteredDefinitions().size());
        assertEquals("moving_platform", palette.getFilteredDefinitions().get(0).id());

        palette.setSearchQuery("switch");
        assertEquals(1, palette.getFilteredDefinitions().size());
        assertEquals("door_switch", palette.getFilteredDefinitions().get(0).id());
    }

    @Test
    void testSelectionModelTieBreakingSmallestAreaThenLowestSeq() {
        SelectionModel selection = new SelectionModel();

        // 3 entities overlapping at (50, 50)
        // Entity 1: large box 64x64, seq 1
        RoomEntityData e1 = new RoomEntityData("large_1", "large", 50, 50, Map.of("collider", Map.of("width", 64, "height", 64, "offsetX", -32, "offsetY", -32)));
        // Entity 2: small box 16x16, seq 5
        RoomEntityData e2 = new RoomEntityData("small_5", "small", 50, 50, Map.of("collider", Map.of("width", 16, "height", 16, "offsetX", -8, "offsetY", -8)));
        // Entity 3: small box 16x16, seq 2
        RoomEntityData e3 = new RoomEntityData("small_2", "small", 50, 50, Map.of("collider", Map.of("width", 16, "height", 16, "offsetX", -8, "offsetY", -8)));

        List<RoomEntityData> entities = List.of(e1, e2, e3);
        RoomEntityData picked = selection.pickEntity(50, 50, entities, Map.of());

        assertNotNull(picked);
        // Between small_5 and small_2, lowest sequence number 2 wins!
        assertEquals("small_2", picked.instanceId(), "Deterministic tie-breaking: smallest area, then lowest seq");
    }

    @Test
    void testGizmoModelPlatformTrajectoryAndHandleHitTest() {
        RoomEntityData platEntity = new RoomEntityData("plat_1", "moving_platform", 100, 200, Map.of(
            "platform", Map.of("travelX", 80, "travelY", -40)
        ));

        GizmoModel.PlatformGizmo gizmo = GizmoModel.computePlatformGizmo(platEntity, null);
        assertNotNull(gizmo);
        assertEquals(100, gizmo.startX());
        assertEquals(200, gizmo.startY());
        assertEquals(180, gizmo.endX());
        assertEquals(160, gizmo.endY());

        // Hit-test handle at (180, 160)
        assertTrue(GizmoModel.hitTestHandle(182, 161, gizmo.endX(), gizmo.endY(), 6));
        assertFalse(GizmoModel.hitTestHandle(195, 160, gizmo.endX(), gizmo.endY(), 6));
    }

    @Test
    void testInspectorModelBuildsNodesAndHandlesOverrides() {
        ComponentSchemaRegistry registry = ComponentSchemaRegistry.createDefault();
        EntityDefinition def = new EntityDefinition(1, "moving_platform", List.of(), "#4488FF", true, Map.of(
            "platform", Map.of("travelX", 0, "travelY", -48, "speed", 32.0f, "crushMode", "BLOCK")
        ));

        RoomEntityData entity = new RoomEntityData("plat_1", "moving_platform", 100, 100, Map.of(
            "platform", Map.of("travelX", 64)
        ));

        InspectorModel inspector = new InspectorModel(entity, def, registry);
        List<InspectorModel.ComponentNode> nodes = inspector.buildComponentNodes();
        assertEquals(1, nodes.size());

        InspectorModel.ComponentNode platNode = nodes.get(0);
        assertEquals("platform", platNode.componentId());

        InspectorModel.FieldNode travelXField = platNode.fields().stream().filter(f -> f.field().name().equals("travelX")).findFirst().orElseThrow();
        assertTrue(travelXField.isOverridden(), "travelX should be marked overridden");
        assertEquals(64, ((Number) travelXField.currentValue()).intValue());

        InspectorModel.FieldNode speedField = platNode.fields().stream().filter(f -> f.field().name().equals("speed")).findFirst().orElseThrow();
        assertFalse(speedField.isOverridden(), "speed is not overridden, should use definition base");
        assertEquals(32.0f, ((Number) speedField.currentValue()).floatValue());

        // Test override mutation
        Map<String, Map<String, Object>> updated = InspectorModel.setOverride(entity.overrides(), "platform", "speed", 60.0f);
        assertTrue(updated.get("platform").containsKey("speed"));
        assertEquals(60.0f, ((Number) updated.get("platform").get("speed")).floatValue());

        Map<String, Map<String, Object>> removed = InspectorModel.removeOverride(updated, "platform", "travelX");
        assertFalse(removed.get("platform").containsKey("travelX"));
        assertTrue(removed.get("platform").containsKey("speed"));
    }
}
