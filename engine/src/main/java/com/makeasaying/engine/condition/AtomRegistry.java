package com.makeasaying.engine.condition;

import java.util.HashSet;
import java.util.Set;

/**
 * Extensible atom registry per M7a brief.
 * Flags atoms registered in M7a; items/stats/cutscenes registered in future milestones.
 */
public final class AtomRegistry {
    private final Set<String> registeredAtoms = new HashSet<>();

    public AtomRegistry() {
        // Core M7a flag atoms
        registeredAtoms.add("flag");
        registeredAtoms.add("flag_set");
    }

    public void registerAtom(String atomName) {
        if (atomName != null) {
            registeredAtoms.add(atomName);
        }
    }

    public boolean isRegistered(String atomName) {
        return atomName != null && registeredAtoms.contains(atomName);
    }
}
