package com.makeasaying.engine.world;

/**
 * States of the room transition state machine per M5b brief.
 */
public enum RoomTransitionState {
    IDLE,
    FADE_OUT,
    HOLD_WAIT_SWAP,
    FADE_IN
}
