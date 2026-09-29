package io.forgeloop.runner;

/** Stable policy-hold classes mapped to control-plane escalation reasons. */
public enum HoldClass {
    RULE_INPUT_MISSING,
    PREREQUISITE_MISSING,
    RULE_FAILED,
    BOUNDARY_BREACHED
}
