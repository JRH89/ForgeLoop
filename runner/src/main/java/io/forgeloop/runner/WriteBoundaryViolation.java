package io.forgeloop.runner;

/** Raised when a provider proposes a path outside its role-derived test write boundary. */
public final class WriteBoundaryViolation extends IllegalArgumentException {
    public WriteBoundaryViolation(String message) { super(message); }
}
