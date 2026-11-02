package com.makeasaying.editor.map.model;

import com.makeasaying.engine.entity.EntityDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Pure model for the entity palette tool window.
 * Filters definitions to only those marked placeable: true.
 * Provides search filtering across definition IDs and tags.
 */
public final class EntityPaletteModel {
    private final List<EntityDefinition> placeableDefinitions = new ArrayList<>();
    private String searchQuery = "";
    private String selectedDefinitionId;

    public EntityPaletteModel(Map<String, EntityDefinition> allDefinitions) {
        setDefinitions(allDefinitions);
    }

    public void setDefinitions(Map<String, EntityDefinition> allDefinitions) {
        placeableDefinitions.clear();
        if (allDefinitions != null) {
            for (EntityDefinition def : allDefinitions.values()) {
                if (def.placeable()) {
                    placeableDefinitions.add(def);
                }
            }
        }
        if (selectedDefinitionId == null && !placeableDefinitions.isEmpty()) {
            selectedDefinitionId = placeableDefinitions.get(0).id();
        }
    }

    public void setSearchQuery(String query) {
        this.searchQuery = (query != null) ? query.trim().toLowerCase() : "";
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public List<EntityDefinition> getFilteredDefinitions() {
        if (searchQuery.isEmpty()) {
            return Collections.unmodifiableList(placeableDefinitions);
        }
        List<EntityDefinition> filtered = new ArrayList<>();
        for (EntityDefinition def : placeableDefinitions) {
            boolean matchesId = def.id().toLowerCase().contains(searchQuery);
            boolean matchesTag = def.tags().stream().anyMatch(t -> t.toLowerCase().contains(searchQuery));
            if (matchesId || matchesTag) {
                filtered.add(def);
            }
        }
        return Collections.unmodifiableList(filtered);
    }

    public String getSelectedDefinitionId() {
        return selectedDefinitionId;
    }

    public void setSelectedDefinitionId(String id) {
        this.selectedDefinitionId = id;
    }

    public EntityDefinition getSelectedDefinition() {
        if (selectedDefinitionId == null) return null;
        for (EntityDefinition def : placeableDefinitions) {
            if (def.id().equals(selectedDefinitionId)) {
                return def;
            }
        }
        return null;
    }
}
