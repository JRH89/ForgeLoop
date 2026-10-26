package io.forgeloop.runner;

/** Classifies failures so outages cannot be confused with an agent-completed task. */
public class ProviderException extends Exception {
    private final boolean retryable;
    private final Integer httpStatus;
    private final String category;
    private final ProviderResult receipt;
    public ProviderException(String message, boolean retryable, Throwable cause) { this(message, retryable, cause, null); }
    public ProviderException(String message, boolean retryable, Throwable cause, Integer httpStatus) {
        this(message, retryable, cause, httpStatus, retryable ? "TRANSIENT_PROVIDER_FAILURE" : "PERMANENT_PROVIDER_FAILURE", null);
    }
    public ProviderException(String message, boolean retryable, Throwable cause, Integer httpStatus, String category, ProviderResult receipt) {
        super(message, cause);
        this.category = category;
        this.receipt = receipt;
        this.retryable = retryable;
        this.httpStatus = httpStatus;
    }
    public ProviderException(String message, boolean retryable) { this(message, retryable, null); }
    public String category() { return category; }
    /** Local receipt may contain raw response data; never serialize it into control telemetry. */
    public ProviderResult receipt() { return receipt; }
    public boolean retryable() { return retryable; }
    public Integer httpStatus() { return httpStatus; }
}
