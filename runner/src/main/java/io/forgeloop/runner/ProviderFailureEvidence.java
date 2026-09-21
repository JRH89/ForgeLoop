package io.forgeloop.runner;

/** Safe failure metadata for an operator: excludes provider output, prompts, source, and credentials. */
public record ProviderFailureEvidence(String provider, String model, boolean retryable, String category) {
    public ProviderFailureEvidence {
        if (provider == null || provider.isBlank() || model == null || model.isBlank() || category == null || category.isBlank()) {
            throw new IllegalArgumentException("Provider failure evidence is invalid");
        }
    }
    public static ProviderFailureEvidence from(ProviderExecutionPolicy policy, ProviderException failure) {
        return new ProviderFailureEvidence(policy.provider(), policy.model(), failure.retryable(), failure.retryable() ? "TRANSIENT_PROVIDER_FAILURE" : "PERMANENT_PROVIDER_FAILURE");
    }
}
