package com.kahga.pluse.electioncycle.entity;

public enum CycleStatus {
    /** The one cycle new sentiment is recorded into. Exactly one at a time. */
    OPEN,
    /** Finished. Its data stays, which is the whole point of cycles (FR-A11). */
    CLOSED
}
