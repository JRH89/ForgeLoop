package io.forgeloop.runner;

/** Content-free diagnostic summary suitable for printing after a live provider tool-call check. */
public record ProviderToolCheckResult(StopReason firstStopReason, StopReason secondStopReason,
                                      long inputTokens, long outputTokens, int firstAttempts, int secondAttempts) {
    public ProviderToolCheckResult {
        if (firstStopReason == null || secondStopReason == null || inputTokens < 0 || outputTokens < 0
                || firstAttempts < 1 || firstAttempts > 3 || secondAttempts < 1 || secondAttempts > 3)
            throw new IllegalArgumentException("Provider tool check result is invalid");
    }
}
