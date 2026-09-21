package io.forgeloop.runner;

/** Final bounded provider failure retaining classification and number of attempts, but no request content. */
public final class ProviderExecutionFailure extends Exception {
    private final ProviderException providerFailure;
    private final int attemptCount;
    public ProviderExecutionFailure(ProviderException providerFailure, int attemptCount) {
        super(providerFailure.getMessage(), providerFailure);
        this.providerFailure = providerFailure; this.attemptCount = attemptCount;
    }
    public ProviderException providerFailure() { return providerFailure; }
    public int attemptCount() { return attemptCount; }
}
