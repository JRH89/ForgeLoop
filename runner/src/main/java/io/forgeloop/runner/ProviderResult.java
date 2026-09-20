package io.forgeloop.runner;

/** Normalized provider result; caller treats provider text as untrusted until its task schema validates it. */
public record ProviderResult(String output, long inputTokens, long outputTokens, String providerRequestId) {
    public ProviderResult {
        if (output == null || output.isBlank() || inputTokens < 0 || outputTokens < 0) throw new IllegalArgumentException("Provider result is invalid");
    }
}
