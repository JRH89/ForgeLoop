package io.forgeloop.runner;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Redacted provider telemetry: no prompts, completions, credentials, or repository paths are retained. */
public record ProviderUsageEvidence(String provider, String model, String requestIdDigest, long inputTokens, long outputTokens,
                                    int attemptCount, long estimatedCostMicros, boolean costKnown) {
    public ProviderUsageEvidence {
        if (provider == null || provider.isBlank() || model == null || model.isBlank() || inputTokens < 0 || outputTokens < 0 || attemptCount < 1 || attemptCount > 3 || estimatedCostMicros < 0 || (!costKnown && estimatedCostMicros != 0)) {
            throw new IllegalArgumentException("Provider usage evidence is invalid");
        }
    }
    public static ProviderUsageEvidence from(ProviderExecutionPolicy policy, ProviderExecutionResult execution) {
        return from(policy, execution, ProviderCostEstimate.unknown());
    }
    public static ProviderUsageEvidence from(ProviderExecutionPolicy policy, ProviderExecutionResult execution, ProviderCostEstimate cost) {
        return from(policy, execution, cost, java.util.UUID.randomUUID().toString());
    }
    public static ProviderUsageEvidence from(ProviderExecutionPolicy policy, ProviderExecutionResult execution,
                                             ProviderCostEstimate cost, String correlationId) {
        if (correlationId == null || correlationId.isBlank()) throw new IllegalArgumentException("Provider usage correlation is required");
        ProviderResult result = execution.result();
        String providerRequestId = result.providerRequestId();
        String digestInput = providerRequestId == null || providerRequestId.isBlank() ? correlationId : providerRequestId;
        return new ProviderUsageEvidence(policy.provider(), policy.model(), digest(digestInput), result.inputTokens(), result.outputTokens(), execution.attemptCount(), cost.estimatedCostMicros(), cost.known());
    }

    public static ProviderUsageEvidence fromTurn(ProviderExecutionPolicy policy, ConversationExecution execution,
                                                  ProviderCostEstimate cost, String correlationId) {
        if (policy == null || execution == null || cost == null || correlationId == null || correlationId.isBlank())
            throw new IllegalArgumentException("Provider conversation usage inputs are invalid");
        ConversationTurn turn = execution.turn();
        String requestId = turn.providerRequestId();
        String digestInput = requestId == null || requestId.isBlank() ? correlationId : requestId;
        return new ProviderUsageEvidence(policy.provider(), policy.model(), digest(digestInput), turn.inputTokens(), turn.outputTokens(),
                execution.attemptCount(), cost.estimatedCostMicros(), cost.known());
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
