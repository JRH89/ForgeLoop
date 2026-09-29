package io.forgeloop.runner;

/** Redacted provider attempt submitted through an authenticated task lease. */
public record ProviderAttemptReport(String provider, String model, String requestIdDigest, long inputTokens,
                                    long outputTokens, int attemptCount, long estimatedCostMicros, boolean costKnown, String outcome,
                                    boolean retryable, String category, String answeredModel) {
    public ProviderAttemptReport(String provider, String model, String requestIdDigest, long inputTokens,
                                 long outputTokens, int attemptCount, long estimatedCostMicros, boolean costKnown,
                                 String outcome, boolean retryable, String category) {
        this(provider, model, requestIdDigest, inputTokens, outputTokens, attemptCount, estimatedCostMicros,
                costKnown, outcome, retryable, category, null);
    }

    public static ProviderAttemptReport succeeded(ProviderUsageEvidence usage) {
        return new ProviderAttemptReport(usage.provider(), usage.model(), usage.requestIdDigest(), usage.inputTokens(),
                usage.outputTokens(), usage.attemptCount(), usage.estimatedCostMicros(), usage.costKnown(), "SUCCEEDED", false, "COMPLETED", usage.answeredModel());
    }
    public static ProviderAttemptReport failed(ProviderFailureEvidence failure) {
        return new ProviderAttemptReport(failure.provider(), failure.model(), failure.requestIdDigest(), 0, 0,
                failure.attemptCount(), 0, false, "FAILED", failure.retryable(), failure.category(), null);
    }
    public static ProviderAttemptReport rejected(ProviderUsageEvidence usage, String category) {
        return new ProviderAttemptReport(usage.provider(), usage.model(), usage.requestIdDigest(), usage.inputTokens(),
                usage.outputTokens(), usage.attemptCount(), usage.estimatedCostMicros(), usage.costKnown(), "FAILED", false, category, usage.answeredModel());
    }
}
