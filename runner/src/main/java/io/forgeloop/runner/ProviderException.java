package io.forgeloop.runner;

/** Classifies failures so outages cannot be confused with an agent-completed task. */
public class ProviderException extends Exception {
    private final boolean retryable;
    private final Integer httpStatus;
    public ProviderException(String message, boolean retryable, Throwable cause) { this(message, retryable, cause, null); }
    public ProviderException(String message, boolean retryable, Throwable cause, Integer httpStatus) {
        super(message, cause);
        this.retryable = retryable;
        this.httpStatus = httpStatus;
    }
    public ProviderException(String message, boolean retryable) { this(message, retryable, null); }
    public boolean retryable() { return retryable; }
    public Integer httpStatus() { return httpStatus; }
}
