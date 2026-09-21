package io.forgeloop.runner;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Redacted provider telemetry: no prompts, completions, credentials, or repository paths are retained. */
public record ProviderUsageEvidence(String provider, String model, String requestIdDigest, long inputTokens, long outputTokens) {
    public ProviderUsageEvidence {
        if (provider == null || provider.isBlank() || model == null || model.isBlank() || inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("Provider usage evidence is invalid");
        }
    }
    public static ProviderUsageEvidence from(ProviderExecutionPolicy policy, ProviderResult result) {
        return new ProviderUsageEvidence(policy.provider(), policy.model(), digest(result.providerRequestId()), result.inputTokens(), result.outputTokens());
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
