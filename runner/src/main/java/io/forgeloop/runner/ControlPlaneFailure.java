package io.forgeloop.runner;

/** Safe transport classification: never include response bodies, credentials, or repository data. */
public final class ControlPlaneFailure extends IllegalStateException {
    private final boolean retryable;
    public ControlPlaneFailure(String reason, boolean retryable) {
        super(reason);
        this.retryable = retryable;
    }
    public boolean retryable() { return retryable; }
}
