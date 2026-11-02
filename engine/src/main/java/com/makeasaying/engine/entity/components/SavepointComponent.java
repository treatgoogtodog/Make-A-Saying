package com.makeasaying.engine.entity.components;

/**
 * Marker and metadata component for checkpoint savepoints per M7b brief.
 * Pure data POJO without logic or object pointers.
 */
public final class SavepointComponent {
    // Reserved for future savepoint properties (e.g., active, prompt label)
    public boolean active = true;

    public SavepointComponent(boolean active) {
        this.active = active;
    }

    public SavepointComponent() {
        this(true);
    }
}
