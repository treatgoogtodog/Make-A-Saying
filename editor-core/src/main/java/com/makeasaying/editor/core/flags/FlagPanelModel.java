package com.makeasaying.editor.core.flags;

import com.makeasaying.engine.flags.FlagDefinition;
import com.makeasaying.engine.flags.FlagRegistry;
import com.makeasaying.engine.flags.FlagScope;
import com.makeasaying.engine.flags.FlagStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pure state model for the Flag Debug Panel per M7a brief.
 * Holds search/scope filters, visibility, and mutation helper methods.
 * Contains no ImGui or rendering dependencies.
 */
public final class FlagPanelModel {
    private boolean visible = false;
    private boolean editable = true;
    private String searchFilter = "";
    private FlagScope scopeFilter = null; // null means ALL

    public FlagPanelModel() {}

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void toggleVisible() {
        this.visible = !this.visible;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    public String getSearchFilter() {
        return searchFilter;
    }

    public void setSearchFilter(String searchFilter) {
        this.searchFilter = searchFilter != null ? searchFilter.trim() : "";
    }

    public FlagScope getScopeFilter() {
        return scopeFilter;
    }

    public void setScopeFilter(FlagScope scopeFilter) {
        this.scopeFilter = scopeFilter;
    }

    public List<FlagDefinition> filter(FlagRegistry registry) {
        if (registry == null) return Collections.emptyList();

        List<FlagDefinition> result = new ArrayList<>();
        String query = searchFilter.toLowerCase(Locale.ROOT);

        for (int i = 0; i < registry.size(); i++) {
            FlagDefinition def = registry.get(i);
            if (def == null) continue;

            if (scopeFilter != null && def.scope() != scopeFilter) {
                continue;
            }

            if (!query.isEmpty() && !def.id().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }

            result.add(def);
        }
        return result;
    }

    public void requestToggle(FlagStore store, int flagIndex, int source) {
        if (!editable || store == null) return;
        store.toggle(flagIndex, source);
    }

    public void requestSet(FlagStore store, int flagIndex, int value, int source) {
        if (!editable || store == null) return;
        store.set(flagIndex, value, source);
    }

    public void requestAdd(FlagStore store, int flagIndex, int delta, int source) {
        if (!editable || store == null) return;
        store.add(flagIndex, delta, source);
    }

    public void requestClear(FlagStore store, int flagIndex, int source) {
        if (!editable || store == null) return;
        store.clear(flagIndex, source);
    }
}
