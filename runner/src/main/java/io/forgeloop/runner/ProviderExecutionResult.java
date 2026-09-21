package io.forgeloop.runner;

/** Successful provider result together with the exact bounded-attempt count. */
public record ProviderExecutionResult(ProviderResult result, int attemptCount) {
    public ProviderExecutionResult {
        if (result == null || attemptCount < 1 || attemptCount > 3) throw new IllegalArgumentException("Provider execution result is invalid");
    }
}
