package io.forgeloop.control.application;

/** Redacted runner report for a bounded provider execution. */
public record ProviderAttemptSubmission(String provider, String model, String requestIdDigest, long inputTokens,
                                        long outputTokens, int attemptCount, String outcome,
                                        long estimatedCostMicros, boolean costKnown, boolean retryable, String category) {
    public ProviderAttemptSubmission {
        if (provider == null || !provider.matches("anthropic|openai|gemini|local") || model == null || model.isBlank() || model.length() > 255) throw new IllegalArgumentException("Provider and model are invalid");
        if (requestIdDigest == null || !requestIdDigest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Provider request digest is invalid");
        if (inputTokens < 0 || outputTokens < 0 || attemptCount < 1 || attemptCount > 3) throw new IllegalArgumentException("Provider usage is invalid");
        if (estimatedCostMicros < 0 || (!costKnown && estimatedCostMicros != 0)) throw new IllegalArgumentException("Provider cost estimate is invalid");
        if (!"SUCCEEDED".equals(outcome) && !"FAILED".equals(outcome)) throw new IllegalArgumentException("Provider outcome is invalid");
        if (category == null || category.isBlank() || category.length() > 80) throw new IllegalArgumentException("Provider outcome category is invalid");
    }
}
