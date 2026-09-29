package io.forgeloop.runner;

import java.nio.charset.StandardCharsets;

/** Conservative provider-turn cost bound derived from the exact serialized request and output cap. */
public record TurnCostBound(long inputTokenUpperBound, long outputTokenUpperBound,
                            long reservedMicros, boolean costKnown, int serializedRequestBytes) {
    public TurnCostBound {
        if (inputTokenUpperBound < 0 || outputTokenUpperBound < 0 || reservedMicros < 0 || serializedRequestBytes < 0)
            throw new IllegalArgumentException("Turn cost bound is invalid");
    }

    /** Uses a byte-per-token ceiling, then grows the previous observed input count by serialized request growth. */
    public static TurnCostBound calculate(String serializedRequest, long previousSerializedBytes,
                                          long previousInputTokens, int outputTokenCap,
                                          ProviderExecutionPolicy policy, ProviderCostCalculator costs) {
        if (serializedRequest == null || previousSerializedBytes < 0 || previousInputTokens < 0
                || outputTokenCap < 0 || policy == null || costs == null)
            throw new IllegalArgumentException("Turn cost bound inputs are invalid");
        int currentBytes = serializedRequest.getBytes(StandardCharsets.UTF_8).length;
        long inputUpperBound = previousSerializedBytes == 0 ? currentBytes
                : Math.addExact(previousInputTokens, Math.max(0L, (long) currentBytes - previousSerializedBytes));
        ProviderCostEstimate estimate = costs.fromTokens(policy, inputUpperBound, outputTokenCap);
        long reservation = estimate.known() ? Math.max(1, estimate.estimatedCostMicros()) : 0;
        if (reservation > 1_000_000_000_000L) throw new IllegalArgumentException("Turn cost bound exceeds the reservation limit");
        return new TurnCostBound(inputUpperBound, outputTokenCap, reservation, estimate.known(), currentBytes);
    }
}
