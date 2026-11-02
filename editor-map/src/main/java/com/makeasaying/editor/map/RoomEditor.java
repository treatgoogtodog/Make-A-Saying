package com.makeasaying.editor.map;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.makeasaying.data.world.RoomLoader;
import com.makeasaying.data.world.RoomWriter;
import com.makeasaying.data.world.ValidationFinding;
import com.makeasaying.editor.core.command.UndoStack;
import com.makeasaying.editor.map.commands.ChangeTilesetAliasCommand;
import com.makeasaying.editor.map.commands.ExitCommands;
import com.makeasaying.editor.map.commands.ResizeRoomCommand;
import com.makeasaying.editor.map.commands.SpawnPointCommands;
import com.makeasaying.editor.map.commands.TilesetManagementCommands;
import com.makeasaying.editor.map.dialogs.SaveConfirmModel;
import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.editor.map.hotreload.EditorHotReloadHandler;
import com.makeasaying.editor.map.tools.EditorTool;
import com.makeasaying.editor.map.tools.EraserTool;
import com.makeasaying.editor.map.tools.EyeDropperTool;
import com.makeasaying.editor.map.tools.FloodFillTool;
import com.makeasaying.editor.map.tools.LineTool;
import com.makeasaying.editor.map.tools.PencilTool;
import com.makeasaying.editor.map.tools.RectTool;
import com.makeasaying.editor.map.validator.EditorValidatorPresenter;
import com.makeasaying.editor.map.view.RoomViewport;
import com.makeasaying.engine.entity.PlayerFactory;
import com.makeasaying.engine.entity.TileShape;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.render.RoomRenderer;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomExit;
import com.makeasaying.engine.world.RoomLayer;
import com.makeasaying.engine.world.RoomLayerType;
import com.makeasaying.engine.world.SpawnPoint;
import com.makeasaying.engine.world.TileDef;
import com.makeasaying.engine.world.Tileset;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Main in-game room editor authored in Dear ImGui per M6a brief.
 * Hosts docking layout, viewport, layers, palette, inspector, validator, and modals.
 */
public final class RoomEditor implements Disposable {
    private final RoomEditorState state;
    private final RoomViewport viewport;
    private final RoomRenderer roomRenderer;
    private final Map<String, Tileset> tilesets;

    private long frameCounter = 0;

    // Temporary string buffers for ImGui text inputs
    private final ImString idInput = new ImString(64);
    private final ImInt widthInput = new ImInt();
    private final ImInt heightInput = new ImInt();
    private final ImString newAliasInput = new ImString(16);
    private final ImString newTilesetIdInput = new ImString(32);

    private final ImString entitySearchInput = new ImString(32);

    public RoomEditor(
        RoomDocument initialDoc,
        Path initialPath,
        RoomRenderer roomRenderer,
        Map<String, Tileset> tilesets,
        Map<String, com.makeasaying.engine.entity.EntityDefinition> entityDefs,
        com.makeasaying.engine.schema.ComponentSchemaRegistry schemaRegistry
    ) {
        this.state = new RoomEditorState(initialDoc, initialPath);
        this.viewport = new RoomViewport();
        this.roomRenderer = roomRenderer;
        this.tilesets = tilesets;

        com.makeasaying.engine.schema.ComponentSchemaRegistry reg = (schemaRegistry != null)
            ? schemaRegistry
            : com.makeasaying.engine.schema.ComponentSchemaRegistry.createDefault();
        this.state.setSchemaRegistry(reg);
        this.state.setEntityDefs(entityDefs != null ? entityDefs : loadEntityDefinitions(reg));
        this.state.setEntityPaletteModel(new com.makeasaying.editor.map.model.EntityPaletteModel(this.state.getEntityDefs()));

        // Initialize hot reload handler
        this.state.setHotReloadHandler(new EditorHotReloadHandler(this::loadFromDisk));

        // Connect eye dropper callback to palette selection
        if (state.getActiveTool() instanceof EyeDropperTool) {
            state.setActiveTool(new EyeDropperTool(state::setActiveTileToken));
        }
    }

    public RoomEditor(
        RoomDocument initialDoc,
        Path initialPath,
        RoomRenderer roomRenderer,
        Map<String, Tileset> tilesets
    ) {
        this(initialDoc, initialPath, roomRenderer, tilesets, null, null);
    }

    private static Map<String, com.makeasaying.engine.entity.EntityDefinition> loadEntityDefinitions(com.makeasaying.engine.schema.ComponentSchemaRegistry reg) {
        Map<String, com.makeasaying.engine.entity.EntityDefinition> defs = new java.util.LinkedHashMap<>();
        Path entDir = Path.of("content/entities");
        if (!Files.exists(entDir)) entDir = Path.of("..", "content", "entities");
        if (Files.exists(entDir)) {
            try (var stream = Files.list(entDir)) {
                for (Path p : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                    try {
                        com.makeasaying.engine.entity.EntityDefinition def = com.makeasaying.data.entity.EntityDefinitionLoader.load(p, reg);
                        defs.put(def.id(), def);
                    } catch (Exception e) {
                        Log.error(LogCategory.DATA, "Failed to load entity definition %s: %s", p, e.getMessage());
                    }
                }
            } catch (IOException ignored) {}
        }
        return defs;
    }

    public RoomEditorState getState() {
        return state;
    }

    public void updateAndRender(float delta) {
        frameCounter++;
        state.getValidatorPresenter().update(frameCounter, state.getDocument(), tilesets, state.getEntityDefs(), state.getSchemaRegistry());

        // 1. Main menu bar
        renderMainMenuBar();

        // 2. Toolbar
        renderToolbar();

        // 3. Viewport Window
        ImGui.begin("Viewport##RoomViewport", ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
        viewport.render(state, roomRenderer, tilesets);
        ImGui.end();

        // 4. Layers Window
        renderLayersPanel();

        // 5. Tileset Palette Window
        renderPalettePanel();

        // 6. Entity Palette Window
        renderEntityPalettePanel();

        // 7. Inspector Window
        renderInspectorPanel();

        // 8. Validator Window
        renderValidatorPanel();

        // 9. Modals
        renderModals();
    }

    private void renderMainMenuBar() {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("New Room...")) {
                    ImGui.openPopup("New Room Modal");
                }
                if (ImGui.menuItem("Save", "Ctrl+S")) {
                    requestSave();
                }
                if (ImGui.menuItem("Revert from Disk", null, false, state.getCurrentFilePath() != null)) {
                    if (state.getCurrentFilePath() != null) {
                        loadFromDisk(state.getCurrentFilePath());
                    }
                }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Edit")) {
                UndoStack stack = state.getDocument().getUndoStack();
                if (ImGui.menuItem("Undo " + stack.getUndoActionName(), "Ctrl+Z", false, stack.canUndo())) {
                    stack.undo();
                }
                if (ImGui.menuItem("Redo " + stack.getRedoActionName(), "Ctrl+Y", false, stack.canRedo())) {
                    stack.redo();
                }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("View")) {
                if (ImGui.menuItem("Show Grid", null, state.isShowGrid())) {
                    state.setShowGrid(!state.isShowGrid());
                }
                if (ImGui.menuItem("Show Collision Shapes", null, state.isShowCollision())) {
                    state.setShowCollision(!state.isShowCollision());
                }
                if (ImGui.menuItem("Show 320x180 View Rect", null, state.isShowViewRect())) {
                    state.setShowViewRect(!state.isShowViewRect());
                }
                if (ImGui.menuItem("Dim Other Layers", null, state.isDimOtherLayers())) {
                    state.setDimOtherLayers(!state.isDimOtherLayers());
                }
                ImGui.separator();
                if (ImGui.menuItem("Center View on Room")) {
                    state.setCamX(0f);
                    state.setCamY(0f);
                }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Mode")) {
                if (!state.isPlaying()) {
                    if (ImGui.menuItem("Start Play (F7)")) {
                        startPlay();
                    }
                } else {
                    if (ImGui.menuItem("Stop Play (F8)")) {
                        stopPlay();
                    }
                }
                ImGui.endMenu();
            }

            ImGui.endMainMenuBar();
        }
    }

    private void renderToolbar() {
        ImGui.begin("Tools##RoomToolbar", ImGuiWindowFlags.AlwaysAutoResize);

        // Play / Stop controls
        if (!state.isPlaying()) {
            if (ImGui.button("▶ Play (F7)")) {
                startPlay();
            }
        } else {
            if (ImGui.button("⏹ Stop (F8)")) {
                stopPlay();
            }
        }

        ImGui.sameLine();
        ImGui.separator();
        ImGui.sameLine();

        // Tool buttons
        selectToolButton("Pencil", PencilTool.class, () -> new PencilTool());
        ImGui.sameLine();
        selectToolButton("Eraser", EraserTool.class, () -> new EraserTool());
        ImGui.sameLine();
        selectToolButton("Line", LineTool.class, () -> new LineTool());
        ImGui.sameLine();
        selectToolButton("Rect (Out)", RectTool.class, () -> new RectTool(false), !((state.getActiveTool() instanceof RectTool rt) && rt.isFilled()));
        ImGui.sameLine();
        selectToolButton("Rect (Fill)", RectTool.class, () -> new RectTool(true), ((state.getActiveTool() instanceof RectTool rt) && rt.isFilled()));
        ImGui.sameLine();
        selectToolButton("Flood Fill", FloodFillTool.class, () -> new FloodFillTool());
        ImGui.sameLine();
        selectToolButton("Eye Dropper", EyeDropperTool.class, () -> new EyeDropperTool(state::setActiveTileToken));

        ImGui.sameLine();
        ImGui.separator();
        ImGui.sameLine();

        // Snap toggle
        boolean snap = state.isEntitySnapToGrid();
        if (ImGui.checkbox("Grid Snap##ent_snap", snap)) {
            state.setEntitySnapToGrid(!snap);
        }
        ImGui.sameLine();
        ImGui.separator();
        ImGui.sameLine();

        // Zoom controls
        ImGui.text(String.format("Zoom: %dx", state.getZoom()));
        ImGui.sameLine();
        if (ImGui.button("-##ZoomMinus") && state.getZoom() > 1) {
            state.setZoom(state.getZoom() - 1);
        }
        ImGui.sameLine();
        if (ImGui.button("+##ZoomPlus") && state.getZoom() < 8) {
            state.setZoom(state.getZoom() + 1);
        }

        ImGui.end();
    }

    private void selectToolButton(String label, Class<?> toolClass, java.util.function.Supplier<EditorTool> factory) {
        selectToolButton(label, toolClass, factory, toolClass.isInstance(state.getActiveTool()));
    }

    private void selectToolButton(String label, Class<?> toolClass, java.util.function.Supplier<EditorTool> factory, boolean isSelected) {
        if (isSelected) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.9f, 1.0f);
        }
        if (ImGui.button(label)) {
            state.setActiveTool(factory.get());
        }
        if (isSelected) {
            ImGui.popStyleColor();
        }
    }

    private void renderLayersPanel() {
        ImGui.begin("Layers##RoomLayers");

        RoomDocument doc = state.getDocument();
        RoomLayerType[] layers = {
            RoomLayerType.FOREGROUND,
            RoomLayerType.DECORATION,
            RoomLayerType.MAIN,
            RoomLayerType.NEAR_BACKGROUND,
            RoomLayerType.FAR_BACKGROUND
        };

        for (RoomLayerType layer : layers) {
            boolean isSelected = (state.getActiveLayer() == layer);
            boolean isVisible = doc.isLayerVisible(layer);
            boolean isLocked = doc.isLayerLocked(layer);

            // Visibility toggle
            if (ImGui.checkbox("##vis_" + layer.name(), isVisible)) {
                doc.setLayerVisible(layer, !isVisible);
            }
            ImGui.sameLine();

            // Lock toggle
            if (ImGui.checkbox("##lock_" + layer.name(), isLocked)) {
                doc.setLayerLocked(layer, !isLocked);
            }
            ImGui.sameLine();

            // Layer selector
            if (ImGui.selectable(layer.name() + (isLocked ? " [LOCKED]" : ""), isSelected)) {
                state.setActiveLayer(layer);
            }
        }

        ImGui.separator();
        boolean dim = state.isDimOtherLayers();
        if (ImGui.checkbox("Dim inactive layers", dim)) {
            state.setDimOtherLayers(!dim);
        }

        ImGui.end();
    }

    private void renderPalettePanel() {
        ImGui.begin("Tileset Palette##RoomPalette");

        RoomDocument doc = state.getDocument();
        Map<String, String> aliases = doc.getTilesetAliases();

        if (aliases.isEmpty()) {
            ImGui.textColored(0.9f, 0.3f, 0.3f, 1.0f, "No tilesets assigned to this room.");
            ImGui.end();
            return;
        }

        // Dropdown to select active tileset alias
        if (ImGui.beginCombo("Tileset", state.getActiveTilesetAlias())) {
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                String alias = entry.getKey();
                String tsId = entry.getValue();
                boolean isSelected = alias.equals(state.getActiveTilesetAlias());
                if (ImGui.selectable(alias + " (" + tsId + ")", isSelected)) {
                    state.setActiveTilesetAlias(alias);
                }
            }
            ImGui.endCombo();
        }

        String activeTsId = aliases.get(state.getActiveTilesetAlias());
        Tileset ts = (activeTsId != null) ? tilesets.get(activeTsId) : null;

        if (ts != null) {
            ImGui.text(String.format("Tileset: %s (%d tiles)", ts.id(), ts.tiles().size()));
            ImGui.text("Active Tile: " + state.getActiveTileToken());
            ImGui.separator();

            // Display grid of tiles
            int cols = 6;
            int colIndex = 0;

            for (Map.Entry<String, TileDef> entry : ts.tiles().entrySet()) {
                String tileId = entry.getKey();
                TileDef def = entry.getValue();
                String token = state.getActiveTilesetAlias() + ":" + tileId;
                boolean isCurrent = token.equals(state.getActiveTileToken());

                if (isCurrent) {
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.7f, 0.3f, 1.0f);
                }

                if (ImGui.button(tileId + "##palette", 52, 28)) {
                    state.setActiveTileToken(token);
                }

                if (isCurrent) {
                    ImGui.popStyleColor();
                }

                // Tooltip on hover with shape, role, and tags per brief
                if (ImGui.isItemHovered()) {
                    ImGui.beginTooltip();
                    ImGui.text("Token: " + token);
                    ImGui.text("Shape: " + def.shape());
                    if (def.role() != null) ImGui.text("Role: " + def.role());
                    if (def.tags() != null && !def.tags().isEmpty()) ImGui.text("Tags: " + def.tags());
                    ImGui.endTooltip();
                }

                colIndex++;
                if (colIndex < cols) {
                    ImGui.sameLine();
                } else {
                    colIndex = 0;
                }
            }
            if (colIndex != 0) {
                ImGui.newLine();
            }
        } else {
            ImGui.textColored(0.9f, 0.5f, 0.2f, 1.0f, "Tileset not loaded in memory: " + activeTsId);
        }

        ImGui.end();
    }

    private void renderInspectorPanel() {
        ImGui.begin("Inspector##RoomInspector");

        RoomDocument doc = state.getDocument();

        // 1. Header properties
        ImGui.text("Room ID: " + doc.getId());
        ImGui.text(String.format("Dimensions: %d x %d tiles (%d x %d px)",
            doc.getWidth(), doc.getHeight(), doc.getWidth() * 16, doc.getHeight() * 16));

        // Resize button
        if (ImGui.button("Resize Room...")) {
            ImGui.openPopup("Resize Room Modal");
        }

        ImGui.separator();

        // 2. Tileset Aliases Inspector
        ImGui.text("Tileset Aliases:");
        List<String> aliasKeys = new ArrayList<>(doc.getTilesetAliases().keySet());
        for (String alias : aliasKeys) {
            String tsId = doc.getTilesetAliases().get(alias);
            int usages = doc.countTokenUsages(alias + ":");

            ImGui.bulletText(String.format("'%s' -> %s (%d tiles used)", alias, tsId, usages));
            ImGui.sameLine();

            // Remove button (disabled if in use)
            if (usages > 0) {
                ImGui.beginDisabled();
                ImGui.button("Remove##" + alias);
                ImGui.endDisabled();
                if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
                    ImGui.setTooltip("Cannot remove: " + usages + " tiles currently used in room");
                }
            } else {
                if (ImGui.button("Remove##" + alias)) {
                    doc.getUndoStack().execute(new TilesetManagementCommands.RemoveTilesetCommand(doc, alias));
                }
            }
        }

        // Add tileset mapping input
        ImGui.inputText("New Alias", newAliasInput);
        ImGui.inputText("Tileset ID", newTilesetIdInput);
        if (ImGui.button("Add Tileset Mapping")) {
            String a = newAliasInput.get().trim();
            String tid = newTilesetIdInput.get().trim();
            if (!a.isBlank() && !tid.isBlank()) {
                doc.getUndoStack().execute(new TilesetManagementCommands.AddTilesetCommand(doc, a, tid));
                newAliasInput.set("");
                newTilesetIdInput.set("");
            }
        }

        ImGui.separator();

        // 3. Spawn Points
        ImGui.text("Spawn Points (" + doc.getSpawnPoints().size() + "):");
        List<SpawnPoint> spawns = new ArrayList<>(doc.getSpawnPoints());
        for (SpawnPoint sp : spawns) {
            ImGui.bulletText(String.format("%s: (%d, %d)", sp.id(), sp.x(), sp.y()));
            ImGui.sameLine();
            if (ImGui.button("Focus##sp_" + sp.id())) {
                state.setCamX(sp.x() - 160);
                state.setCamY(sp.y() - 90);
            }
            ImGui.sameLine();
            if (ImGui.button("Delete##sp_" + sp.id())) {
                doc.getUndoStack().execute(new SpawnPointCommands.RemoveSpawnPointCommand(doc, sp));
            }
        }

        ImGui.separator();

        // 4. Exits
        ImGui.text("Exits (" + doc.getExits().size() + "):");
        List<RoomExit> exits = new ArrayList<>(doc.getExits());
        for (RoomExit ex : exits) {
            ImGui.bulletText(String.format("%s: [%d,%d %dx%d] -> %s:%s",
                ex.id(), ex.x(), ex.y(), ex.width(), ex.height(), ex.targetRoom(), ex.targetSpawn()));
            ImGui.sameLine();
            if (ImGui.button("Focus##ex_" + ex.id())) {
                state.setCamX(ex.x() - 160);
                state.setCamY(ex.y() - 90);
            }
            ImGui.sameLine();
            if (ImGui.button("Delete##ex_" + ex.id())) {
                doc.getUndoStack().execute(new ExitCommands.RemoveExitCommand(doc, ex));
            }
        }

        ImGui.separator();

        // 5. Entities Inspector
        ImGui.text("Entities (" + doc.getEntities().size() + "):");
        String selectedId = state.getSelectionModel().getSelectedInstanceId();
        com.makeasaying.engine.world.RoomEntityData selectedEntity = null;

        List<com.makeasaying.engine.world.RoomEntityData> entities = new ArrayList<>(doc.getEntities());
        for (com.makeasaying.engine.world.RoomEntityData ed : entities) {
            boolean isSel = ed.instanceId().equals(selectedId);
            if (isSel) selectedEntity = ed;

            if (ImGui.selectable(ed.instanceId() + " (" + ed.definition() + ")##ent_list", isSel)) {
                state.getSelectionModel().setSelectedInstanceId(ed.instanceId());
            }
            ImGui.sameLine();
            if (ImGui.button("Focus##ent_" + ed.instanceId())) {
                state.setCamX(ed.x() - 160);
                state.setCamY(ed.y() - 90);
                state.getSelectionModel().setSelectedInstanceId(ed.instanceId());
            }
            ImGui.sameLine();
            if (ImGui.button("Delete##ent_" + ed.instanceId())) {
                doc.getUndoStack().execute(new com.makeasaying.editor.map.commands.EntityCommands.DeleteEntityCommand(doc, ed));
                if (isSel) state.getSelectionModel().clearSelection();
            }
        }

        // Selected Entity Schema-Driven Inspector
        if (selectedEntity != null) {
            ImGui.separator();
            ImGui.textColored(0.2f, 0.8f, 1.0f, 1.0f, "Selected: " + selectedEntity.instanceId());
            ImGui.text("Definition: " + selectedEntity.definition());
            ImGui.text(String.format("Position: (%d, %d)", selectedEntity.x(), selectedEntity.y()));

            com.makeasaying.engine.entity.EntityDefinition def = state.getEntityDefs().get(selectedEntity.definition());
            if (def != null && state.getSchemaRegistry() != null) {
                com.makeasaying.editor.map.model.InspectorModel inspModel =
                    new com.makeasaying.editor.map.model.InspectorModel(selectedEntity, def, state.getSchemaRegistry());
                List<com.makeasaying.editor.map.model.InspectorModel.ComponentNode> compNodes = inspModel.buildComponentNodes();

                for (com.makeasaying.editor.map.model.InspectorModel.ComponentNode compNode : compNodes) {
                    if (ImGui.collapsingHeader(compNode.componentId() + "##comp_" + compNode.componentId(), imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
                        for (com.makeasaying.editor.map.model.InspectorModel.FieldNode fn : compNode.fields()) {
                            renderInspectorField(doc, selectedEntity, compNode.componentId(), fn);
                        }
                    }
                }
            }
        }

        ImGui.end();
    }

    private void renderInspectorField(
        RoomDocument doc,
        com.makeasaying.engine.world.RoomEntityData entity,
        String compId,
        com.makeasaying.editor.map.model.InspectorModel.FieldNode fn
    ) {
        com.makeasaying.engine.schema.ComponentField f = fn.field();
        String fieldLabel = f.name() + (fn.isOverridden() ? " [Overridden]" : "");

        if (!fn.isOverridable()) {
            ImGui.text(f.name() + ": " + fn.currentValue() + " (read-only)");
            return;
        }

        switch (f.type()) {
            case INT -> {
                ImInt val = new ImInt(((Number) fn.currentValue()).intValue());
                ImGui.inputInt(fieldLabel + "##" + compId + "_" + f.name(), val);
                if (ImGui.isItemDeactivatedAfterEdit()) {
                    doc.getUndoStack().execute(new com.makeasaying.editor.map.commands.EntityCommands.EditOverrideFieldCommand(
                        doc, entity.instanceId(), compId, f.name(),
                        fn.isOverridden() ? fn.currentValue() : null,
                        val.get()
                    ));
                }
            }
            case FLOAT -> {
                ImFloat val = new ImFloat(((Number) fn.currentValue()).floatValue());
                ImGui.inputFloat(fieldLabel + "##" + compId + "_" + f.name(), val);
                if (ImGui.isItemDeactivatedAfterEdit()) {
                    doc.getUndoStack().execute(new com.makeasaying.editor.map.commands.EntityCommands.EditOverrideFieldCommand(
                        doc, entity.instanceId(), compId, f.name(),
                        fn.isOverridden() ? fn.currentValue() : null,
                        val.get()
                    ));
                }
            }
            case BOOL -> {
                boolean cur = Boolean.TRUE.equals(fn.currentValue());
                if (ImGui.checkbox(fieldLabel + "##" + compId + "_" + f.name(), cur)) {
                    doc.getUndoStack().execute(new com.makeasaying.editor.map.commands.EntityCommands.EditOverrideFieldCommand(
                        doc, entity.instanceId(), compId, f.name(),
                        fn.isOverridden() ? fn.currentValue() : null,
                        !cur
                    ));
                }
            }
            case ENUM -> {
                if (ImGui.beginCombo(fieldLabel + "##" + compId + "_" + f.name(), String.valueOf(fn.currentValue()))) {
                    for (String constant : f.enumConstants()) {
                        boolean sel = constant.equals(String.valueOf(fn.currentValue()));
                        if (ImGui.selectable(constant, sel)) {
                            doc.getUndoStack().execute(new com.makeasaying.editor.map.commands.EntityCommands.EditOverrideFieldCommand(
                                doc, entity.instanceId(), compId, f.name(),
                                fn.isOverridden() ? fn.currentValue() : null,
                                constant
                            ));
                        }
                    }
                    ImGui.endCombo();
                }
            }
            case STRING -> {
                ImString str = new ImString(String.valueOf(fn.currentValue()), 64);
                ImGui.inputText(fieldLabel + "##" + compId + "_" + f.name(), str);
                if (ImGui.isItemDeactivatedAfterEdit()) {
                    doc.getUndoStack().execute(new com.makeasaying.editor.map.commands.EntityCommands.EditOverrideFieldCommand(
                        doc, entity.instanceId(), compId, f.name(),
                        fn.isOverridden() ? fn.currentValue() : null,
                        str.get()
                    ));
                }
            }
        }

        if (fn.isOverridden()) {
            ImGui.sameLine();
            if (ImGui.button("Reset##" + compId + "_" + f.name())) {
                doc.getUndoStack().execute(new com.makeasaying.editor.map.commands.EntityCommands.EditOverrideFieldCommand(
                    doc, entity.instanceId(), compId, f.name(),
                    fn.currentValue(),
                    null
                ));
            }
        }
    }

    private void renderEntityPalettePanel() {
        ImGui.begin("Entity Palette##RoomEntityPalette");

        com.makeasaying.editor.map.model.EntityPaletteModel palette = state.getEntityPaletteModel();
        if (palette == null) {
            ImGui.text("No entity palette available.");
            ImGui.end();
            return;
        }

        if (ImGui.inputText("Search##ent_search", entitySearchInput)) {
            palette.setSearchQuery(entitySearchInput.get());
        }

        ImGui.separator();

        List<com.makeasaying.engine.entity.EntityDefinition> defs = palette.getFilteredDefinitions();
        for (com.makeasaying.engine.entity.EntityDefinition def : defs) {
            boolean isSel = def.id().equals(palette.getSelectedDefinitionId());
            if (isSel) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.9f, 1.0f);
            }
            if (ImGui.button(def.id() + "##def_btn", 160, 28)) {
                palette.setSelectedDefinitionId(def.id());
            }
            if (isSel) {
                ImGui.popStyleColor();
            }

            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("ID: " + def.id());
                ImGui.text("Tags: " + def.tags());
                ImGui.text("Color: " + def.debugColor());
                ImGui.endTooltip();
            }

            ImGui.sameLine();
            if (ImGui.button("Place at Center##" + def.id())) {
                placeEntityAtCenter(def.id());
            }
        }

        ImGui.end();
    }

    private void placeEntityAtCenter(String defId) {
        RoomDocument doc = state.getDocument();
        int seq = doc.allocateEntitySeq();
        String instanceId = defId + "_" + seq;

        int spawnX = (doc.getWidth() * 16) / 2;
        int spawnY = (doc.getHeight() * 16) / 2;
        if (state.isEntitySnapToGrid()) {
            spawnX = (spawnX / 16) * 16;
            spawnY = (spawnY / 16) * 16;
        }

        com.makeasaying.engine.world.RoomEntityData newEntity = new com.makeasaying.engine.world.RoomEntityData(
            instanceId, defId, spawnX, spawnY, java.util.Collections.emptyMap()
        );
        doc.getUndoStack().execute(new com.makeasaying.editor.map.commands.EntityCommands.PlaceEntityCommand(doc, newEntity, seq));
        state.getSelectionModel().setSelectedInstanceId(instanceId);
    }

    private void renderValidatorPanel() {
        ImGui.begin("Validator##RoomValidator");

        EditorValidatorPresenter presenter = state.getValidatorPresenter();

        int errCount = presenter.getErrors().size();
        int warnCount = presenter.getWarnings().size();
        int infoCount = presenter.getInfos().size();

        ImGui.text(String.format("Findings: %d Errors | %d Warnings | %d Infos", errCount, warnCount, infoCount));
        ImGui.separator();

        for (ValidationFinding f : presenter.getAllFindings()) {
            switch (f.severity()) {
                case ERROR -> ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "[ERROR] " + f.code());
                case WARNING -> ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, "[WARN] " + f.code());
                case INFO -> ImGui.textColored(0.4f, 0.7f, 1.0f, 1.0f, "[INFO] " + f.code());
            }

            ImGui.sameLine();
            if (ImGui.selectable(f.location() + ": " + f.message())) {
                EditorValidatorPresenter.LocationTarget target = presenter.parseLocationTarget(f);
                if (target != null) {
                    state.setCamX(target.tileX() * 16 - 160);
                    state.setCamY(target.tileY() * 16 - 90);
                    if (target.layer() != null) {
                        state.setActiveLayer(target.layer());
                    }
                }
            }
        }

        ImGui.end();
    }

    private void renderModals() {
        // 1. New Room Modal
        if (ImGui.beginPopupModal("New Room Modal", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Create New Room Document");
            ImGui.separator();

            ImGui.inputText("Room ID", idInput);
            ImGui.inputInt("Width (tiles)", widthInput);
            ImGui.inputInt("Height (tiles)", heightInput);

            if (ImGui.button("Create", 120, 0)) {
                String id = idInput.get().trim();
                int w = widthInput.get();
                int h = heightInput.get();
                if (!id.isBlank() && w > 0 && h > 0) {
                    RoomDocument newDoc = new RoomDocument(id, w, h);
                    newDoc.getTilesetAliases().put("a", "test_area");
                    newDoc.getSpawnPoints().add(new SpawnPoint("default", 32, 16));
                    state.setDocument(newDoc);
                    state.setCurrentFilePath(Path.of("content/rooms", id + ".json"));
                    ImGui.closeCurrentPopup();
                }
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }

        // 2. Save Confirmation with Errors Modal
        SaveConfirmModel confirmModel = state.getSaveConfirmModel();
        if (confirmModel.isOpen() && confirmModel.getCurrentPrompt() == SaveConfirmModel.PromptType.CONFIRM_SAVE_WITH_ERRORS) {
            ImGui.openPopup("Save with Errors Confirmation");
        }

        if (ImGui.beginPopupModal("Save with Errors Confirmation", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Warning: Room contains validation errors!");
            ImGui.text(String.format("Found %d error(s). Saving invalid rooms may cause gameplay issues.", confirmModel.getErrorCount()));
            ImGui.separator();

            // Default button is Cancel per brief
            if (ImGui.button("Cancel (Recommended)", 160, 0)) {
                confirmModel.cancel();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Save Anyway", 120, 0)) {
                confirmModel.confirm();
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }

        // 3. External File Conflict Modal
        EditorHotReloadHandler hotReload = state.getHotReloadHandler();
        if (hotReload != null && hotReload.isConflictPromptOpen()) {
            ImGui.openPopup("Disk Conflict Prompt");
        }

        if (ImGui.beginPopupModal("Disk Conflict Prompt", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, "File was modified externally on disk!");
            ImGui.text("You have unsaved in-memory edits. What would you like to do?");
            ImGui.separator();

            if (ImGui.button("Keep In-Memory Changes", 180, 0)) {
                hotReload.resolveConflictKeepInMemory();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Reload from Disk", 150, 0)) {
                hotReload.resolveConflictReloadDisk(state.getCurrentFilePath());
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    public void requestSave() {
        if (state.getCurrentFilePath() == null) {
            state.setCurrentFilePath(Path.of("content/rooms", state.getDocument().getId() + ".json"));
        }

        EditorValidatorPresenter validator = state.getValidatorPresenter();
        validator.forceValidate(frameCounter, state.getDocument(), tilesets);

        if (validator.hasErrors()) {
            state.getSaveConfirmModel().promptSaveWithErrors(
                validator.getErrors().size(),
                this::executeSave,
                () -> Log.info(LogCategory.DATA, "Save cancelled by user due to validation errors")
            );
        } else {
            executeSave();
        }
    }

    private void executeSave() {
        try {
            Path path = state.getCurrentFilePath();
            RoomWriter.save(state.getDocument().toRoom(), path);
            state.getDocument().updateSavedFloorOnSave();
            if (state.getHotReloadHandler() != null) {
                state.getHotReloadHandler().onEditorSaved();
            }
            Log.info(LogCategory.DATA, "Saved room %s to %s", state.getDocument().getId(), path);
        } catch (IOException e) {
            Log.error(LogCategory.DATA, "Failed to save room: %s", e, e.getMessage());
        }
    }

    public void loadFromDisk(Path path) {
        try {
            Room loaded = RoomLoader.load(path);
            state.setDocument(RoomDocument.fromRoom(loaded));
            state.setCurrentFilePath(path);
            Log.info(LogCategory.DATA, "Loaded room %s from disk", loaded.id());
        } catch (IOException e) {
            Log.error(LogCategory.DATA, "Failed loading room from %s: %s", e, path, e.getMessage());
        }
    }

    public void startPlay() {
        if (state.isPlaying()) return;

        World world = new World();
        // Spawn player at room spawn point
        SpawnPoint sp = state.getDocument().getSpawnPoints().isEmpty()
            ? new SpawnPoint("default", 32, 16)
            : state.getDocument().getSpawnPoints().get(0);

        long playerId = PlayerFactory.createPlayer(world, sp.x(), sp.y());

        // Spawn room-local entities into simulation world
        if (state.getSchemaRegistry() != null) {
            com.makeasaying.engine.entity.EntitySpawner spawner = new com.makeasaying.engine.entity.EntitySpawner(state.getSchemaRegistry());
            for (com.makeasaying.engine.world.RoomEntityData ed : state.getDocument().getEntities()) {
                if (ed.spawnCondition() != null && state.getFlagStore() != null &&
                    !ed.spawnCondition().evaluate(state.getFlagStore(), state.getDocument().getId())) {
                    continue;
                }
                com.makeasaying.engine.entity.EntityDefinition def = state.getEntityDefs().get(ed.definition());
                if (def != null) {
                    spawner.spawn(world, def, ed.instanceId(), ed.x(), ed.y(), ed.overrides());
                }
            }
        }
        world.applyDeferredLifecycle();

        state.setPlayWorld(world);
        state.setPlayPlayerId(playerId);
        state.setPlaySnapshot(world.snapshot());
        state.setCompositePlaySnapshot(com.makeasaying.engine.entity.CompositeGameSnapshot.capture(
            world, state.getFlagStore(), state.getRoomStateStore()
        ));
        state.setPlaying(true);

        Log.info(LogCategory.ENGINE, "Editor Play mode started for room %s with %d entities",
            state.getDocument().getId(), state.getDocument().getEntities().size());
    }

    public void stopPlay() {
        if (!state.isPlaying()) return;

        if (state.getCompositePlaySnapshot() != null) {
            state.getCompositePlaySnapshot().restore(state.getPlayWorld(), state.getFlagStore(), state.getRoomStateStore());
            state.setCompositePlaySnapshot(null);
        } else if (state.getPlayWorld() != null && state.getPlaySnapshot() != null) {
            state.getPlayWorld().restore(state.getPlaySnapshot());
        }
        state.setPlaying(false);
        state.setPlayWorld(null);
        state.setPlaySnapshot(null);

        Log.info(LogCategory.ENGINE, "Editor Play mode stopped, restored snapshot cleanly");
    }

    @Override
    public void dispose() {
        if (viewport != null) viewport.dispose();
    }
}
