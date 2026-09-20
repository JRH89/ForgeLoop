package io.forgeloop.runner;

/** Classifies failures so outages cannot be confused with an agent-completed task. */
public final class ProviderException extends Exception {
    private final boolean retryable;
    public ProviderException(String message, boolean retryable, Throwable cause) { super(message, cause); this.retryable = retryable; }
    public ProviderException(String message, boolean retryable) { this(message, retryable, null); }
    public boolean retryable() { return retryable; }
}
