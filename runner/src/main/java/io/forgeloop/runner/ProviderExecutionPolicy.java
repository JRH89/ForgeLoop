package io.forgeloop.runner;

/** Immutable local policy that bounds provider retries before any untrusted output is used. */
public record ProviderExecutionPolicy(String provider, String model, int maxAttempts,
                                      java.math.BigDecimal inputUsdPerMillion, java.math.BigDecimal outputUsdPerMillion) {
    public ProviderExecutionPolicy(String provider, String model, int maxAttempts) { this(provider, model, maxAttempts, null, null); }
    public ProviderExecutionPolicy {
        if ((inputUsdPerMillion == null) != (outputUsdPerMillion == null)
                || inputUsdPerMillion != null && (inputUsdPerMillion.signum() < 0 || outputUsdPerMillion.signum() < 0))
            throw new IllegalArgumentException("Both nonnegative input and output USD rates are required");
        if (!java.util.Set.of("anthropic", "openai", "gemini", "local").contains(provider)) {
            throw new IllegalArgumentException("Provider must be anthropic, openai, gemini, or local");
        }
        if (model == null || model.isBlank() || maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("Provider execution policy is invalid");
        }
    }
}
