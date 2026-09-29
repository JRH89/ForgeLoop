package io.forgeloop.runner;

/** Non-retryable mismatch between a replay request and the captured execution record. */
public final class ReplayDivergence extends ProviderException {
    public ReplayDivergence(String message) { super(message, false); }
}
