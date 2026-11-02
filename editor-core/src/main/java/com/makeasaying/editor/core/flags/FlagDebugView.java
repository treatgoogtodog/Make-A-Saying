package com.makeasaying.editor.core.flags;

import com.makeasaying.engine.flags.FlagChangeLogEntry;
import com.makeasaying.engine.flags.FlagDefinition;
import com.makeasaying.engine.flags.FlagRegistry;
import com.makeasaying.engine.flags.FlagScope;
import com.makeasaying.engine.flags.FlagStore;
import com.makeasaying.engine.flags.FlagType;
import imgui.ImGui;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.List;

/**
 * Dear ImGui renderer for the Flag & Variable Debug View per M7a brief.
 * Bound to F2 (debug_flags). Can inspect and live-tweak flags in game and editor play mode.
 */
public final class FlagDebugView {
    private final ImString searchBuffer = new ImString(64);
    private final ImBoolean windowOpen = new ImBoolean(false);

    public void render(
        FlagPanelModel model,
        FlagRegistry registry,
        FlagStore store,
        String activeRoomId
    ) {
        if (model == null || registry == null || store == null) return;
        if (!model.isVisible()) return;

        windowOpen.set(true);
        ImGui.setNextWindowSize(600, 450, imgui.flag.ImGuiCond.FirstUseEver);

        if (ImGui.begin("Flags & Variables (F2)###FlagDebugWindow", windowOpen, ImGuiWindowFlags.None)) {
            // Header / Filters
            if (!model.isEditable()) {
                ImGui.textColored(0.9f, 0.6f, 0.2f, 1.0f, "[Read-Only: Editing / Paused Mode]");
                ImGui.separator();
            }

            ImGui.text("Search:");
            ImGui.sameLine();
            if (ImGui.inputText("##search", searchBuffer)) {
                model.setSearchFilter(searchBuffer.get());
            }

            ImGui.sameLine();
            if (ImGui.button("Clear##search_clear")) {
                searchBuffer.set("");
                model.setSearchFilter("");
            }

            // Scope filter buttons
            ImGui.text("Scope:");
            ImGui.sameLine();
            if (ImGui.radioButton("ALL", model.getScopeFilter() == null)) {
                model.setScopeFilter(null);
            }
            ImGui.sameLine();
            if (ImGui.radioButton("GLOBAL", model.getScopeFilter() == FlagScope.GLOBAL)) {
                model.setScopeFilter(FlagScope.GLOBAL);
            }
            ImGui.sameLine();
            if (ImGui.radioButton("SESSION", model.getScopeFilter() == FlagScope.SESSION)) {
                model.setScopeFilter(FlagScope.SESSION);
            }
            ImGui.sameLine();
            if (ImGui.radioButton("ROOM", model.getScopeFilter() == FlagScope.ROOM)) {
                model.setScopeFilter(FlagScope.ROOM);
            }
            ImGui.sameLine();
            if (ImGui.radioButton("ROOM_TEMP", model.getScopeFilter() == FlagScope.ROOM_TEMP)) {
                model.setScopeFilter(FlagScope.ROOM_TEMP);
            }

            ImGui.separator();

            // Table of flags
            List<FlagDefinition> flags = model.filter(registry);
            int tableFlags = ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.Resizable | ImGuiTableFlags.ScrollY;
            if (ImGui.beginTable("FlagsTable", 5, tableFlags, 0, 220)) {
                ImGui.tableSetupColumn("ID", 0, 180);
                ImGui.tableSetupColumn("Scope", 0, 80);
                ImGui.tableSetupColumn("Type", 0, 60);
                ImGui.tableSetupColumn("Value", 0, 80);
                ImGui.tableSetupColumn("Actions", 0, 120);
                ImGui.tableHeadersRow();

                for (FlagDefinition def : flags) {
                    ImGui.tableNextRow();

                    // Column 0: ID
                    ImGui.tableSetColumnIndex(0);
                    ImGui.text(def.id());
                    if (ImGui.isItemHovered()) {
                        String doc = def.doc() != null && !def.doc().isEmpty() ? def.doc() : "No documentation";
                        ImGui.setTooltip(String.format("%s\nDefault: %d [%d..%d]", doc, def.defaultValue(), def.min(), def.max()));
                    }

                    // Column 1: Scope
                    ImGui.tableSetColumnIndex(1);
                    ImGui.text(def.scope().name());

                    // Column 2: Type
                    ImGui.tableSetColumnIndex(2);
                    ImGui.text(def.type().name());

                    // Column 3: Current Value
                    ImGui.tableSetColumnIndex(3);
                    int currentVal = store.get(def.index(), activeRoomId);
                    if (def.type() == FlagType.BOOL) {
                        if (currentVal == 1) {
                            ImGui.textColored(0.2f, 0.9f, 0.2f, 1.0f, "true");
                        } else {
                            ImGui.textColored(0.8f, 0.3f, 0.3f, 1.0f, "false");
                        }
                    } else if (def.type() == FlagType.ENUM && def.enumValues() != null && currentVal >= 0 && currentVal < def.enumValues().size()) {
                        ImGui.text(def.enumValues().get(currentVal) + " (" + currentVal + ")");
                    } else {
                        ImGui.text(Integer.toString(currentVal));
                    }

                    // Column 4: Actions
                    ImGui.tableSetColumnIndex(4);
                    if (model.isEditable()) {
                        if (def.type() == FlagType.BOOL) {
                            if (ImGui.button("Toggle##" + def.id())) {
                                model.requestToggle(store, def.index(), FlagChangeLogEntry.SOURCE_DEBUG_PANEL);
                            }
                        } else if (def.type() == FlagType.INT) {
                            if (ImGui.button("-1##" + def.id())) {
                                model.requestAdd(store, def.index(), -1, FlagChangeLogEntry.SOURCE_DEBUG_PANEL);
                            }
                            ImGui.sameLine();
                            if (ImGui.button("+1##" + def.id())) {
                                model.requestAdd(store, def.index(), 1, FlagChangeLogEntry.SOURCE_DEBUG_PANEL);
                            }
                        } else if (def.type() == FlagType.ENUM) {
                            if (ImGui.button("Cycle##" + def.id())) {
                                int nextVal = (currentVal + 1);
                                if (def.enumValues() != null && nextVal >= def.enumValues().size()) {
                                    nextVal = 0;
                                }
                                model.requestSet(store, def.index(), nextVal, FlagChangeLogEntry.SOURCE_DEBUG_PANEL);
                            }
                        }
                        ImGui.sameLine();
                        if (ImGui.button("Reset##" + def.id())) {
                            model.requestClear(store, def.index(), FlagChangeLogEntry.SOURCE_DEBUG_PANEL);
                        }
                    } else {
                        ImGui.textDisabled("-");
                    }
                }
                ImGui.endTable();
            }

            ImGui.separator();

            // Bottom section: Change Log Strip
            int logCount = store.getLogCount();
            ImGui.text(String.format("Change Log (%d recent entries):", logCount));

            if (ImGui.beginChild("ChangeLogRegion", 0, 100, true)) {
                for (int i = logCount - 1; i >= 0; i--) {
                    FlagChangeLogEntry entry = store.getLogEntry(i);
                    if (entry == null) continue;
                    FlagDefinition def = registry.get(entry.flagIndex);
                    String flagName = (def != null) ? def.id() : ("#idx" + entry.flagIndex);
                    String srcName = FlagChangeLogEntry.getSourceName(entry.source);

                    ImGui.text(String.format("[T:%d][%s] %s: %d -> %d",
                        entry.tick, srcName, flagName, entry.oldValue, entry.newValue));
                }
                ImGui.endChild();
            }
        }
        ImGui.end();

        if (!windowOpen.get()) {
            model.setVisible(false);
        }
    }
}
