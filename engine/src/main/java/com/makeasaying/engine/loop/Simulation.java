package com.makeasaying.engine.loop;

/**
 * Interface representing fixed-timestep simulation tick listener.
 */
@FunctionalInterface
public interface Simulation {
    void tick(long currentTick);
}
