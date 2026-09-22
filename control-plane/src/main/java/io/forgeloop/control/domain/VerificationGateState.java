package io.forgeloop.control.domain;

/** Authoritative lifecycle for a policy-selected verification gate. */
public enum VerificationGateState {
    PENDING, RUNNING, PASSED, FAILED, TIMED_OUT, SKIPPED_BY_POLICY, MANUAL_OVERRIDE
}
