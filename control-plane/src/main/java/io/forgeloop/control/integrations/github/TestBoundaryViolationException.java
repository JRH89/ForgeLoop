package io.forgeloop.control.integrations.github;

/** Signals a persisted policy hold after publishing a branch that failed the test-first boundary check. */
public final class TestBoundaryViolationException extends RuntimeException {
    public TestBoundaryViolationException(String message) {
        super(message);
    }
}
