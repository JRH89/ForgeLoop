package io.forgeloop.runner;

/** Immutable local policy that bounds provider retries before any untrusted output is used. */
public record ProviderExecutionPolicy(String provider, String model, int maxAttempts) {
    public ProviderExecutionPolicy {
        if (!("anthropic".equals(provider) || "openai".equals(provider))) {
            throw new IllegalArgumentException("Provider must be anthropic or openai");
        }
        if (model == null || model.isBlank() || maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("Provider execution policy is invalid");
        }
    }
}
