package io.forgeloop.runner;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Safe failure metadata for an operator: excludes provider output, prompts, source, and credentials. */
public record ProviderFailureEvidence(String provider, String model, String requestIdDigest, int attemptCount, boolean retryable, String category, long inputTokens, long outputTokens, long estimatedCostMicros, boolean costKnown, String answeredModel) {
    public ProviderFailureEvidence(String provider, String model, String requestIdDigest, int attemptCount, boolean retryable, String category) {
        this(provider, model, requestIdDigest, attemptCount, retryable, category, 0, 0, 0, false, null);
    }
    public ProviderFailureEvidence {
        if (provider == null || provider.isBlank() || model == null || model.isBlank() || requestIdDigest == null || !requestIdDigest.matches("[0-9a-f]{64}") || attemptCount < 1 || attemptCount > 3 || category == null || category.isBlank() || inputTokens < 0 || outputTokens < 0 || estimatedCostMicros < 0 || (!costKnown && estimatedCostMicros != 0)) {
            throw new IllegalArgumentException("Provider failure evidence is invalid");
        }
    }
    public static ProviderFailureEvidence from(ProviderExecutionPolicy policy, ProviderExecutionFailure failure, String correlationId) {
        if (correlationId == null || correlationId.isBlank()) throw new IllegalArgumentException("Provider failure correlation is required");
        String category = failure.providerFailure().category();
        long input = failure.receipts().stream().mapToLong(ProviderResult::inputTokens).sum();
        long output = failure.receipts().stream().mapToLong(ProviderResult::outputTokens).sum();
        ProviderResult receipt = failure.receipts().isEmpty() ? null : failure.receipts().getLast();
        ProviderCostEstimate cost = receipt == null || !failure.usageKnown() ? ProviderCostEstimate.unknown()
                : new ProviderCostCalculator().fromEnvironment(policy, new ProviderResult("receipt", input, output, null));
        String id = receipt == null || receipt.providerRequestId() == null || receipt.providerRequestId().isBlank()
                ? correlationId + "\0" + policy.provider() + "\0" + policy.model() + "\0" + failure.attemptCount() + "\0" + category
                : receipt.providerRequestId();
        return new ProviderFailureEvidence(policy.provider(), policy.model(), digest(id), failure.attemptCount(),
                failure.providerFailure().retryable(), category, input, output, cost.estimatedCostMicros(), cost.known(),
                receipt == null ? null : receipt.answeredModel());
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
