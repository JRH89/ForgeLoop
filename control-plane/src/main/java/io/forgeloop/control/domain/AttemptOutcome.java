package io.forgeloop.control.domain;

/** Meaning of one closed execution attempt, independent of the run's final state. */
public enum AttemptOutcome {
    CLEAN,
    FINDINGS,
    HARNESS_FAILURE,
    STOPPED
}
