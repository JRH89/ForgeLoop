package io.forgeloop.runner;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Safe failure metadata for an operator: excludes provider output, prompts, source, and credentials. */
public record ProviderFailureEvidence(String provider, String model, String requestIdDigest, int attemptCount, boolean retryable, String category) {
    public ProviderFailureEvidence {
        if (provider == null || provider.isBlank() || model == null || model.isBlank() || requestIdDigest == null || !requestIdDigest.matches("[0-9a-f]{64}") || attemptCount < 1 || attemptCount > 3 || category == null || category.isBlank()) {
            throw new IllegalArgumentException("Provider failure evidence is invalid");
        }
    }
    public static ProviderFailureEvidence from(ProviderExecutionPolicy policy, ProviderExecutionFailure failure, String correlationId) {
        if (correlationId == null || correlationId.isBlank()) throw new IllegalArgumentException("Provider failure correlation is required");
        String category = failure.providerFailure().retryable() ? "TRANSIENT_PROVIDER_FAILURE" : "PERMANENT_PROVIDER_FAILURE";
        return new ProviderFailureEvidence(policy.provider(), policy.model(), digest(correlationId + "\0" + policy.provider() + "\0" + policy.model() + "\0" + failure.attemptCount() + "\0" + category), failure.attemptCount(), failure.providerFailure().retryable(), category);
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
