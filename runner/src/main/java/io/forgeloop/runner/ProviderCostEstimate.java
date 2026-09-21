package io.forgeloop.runner;

/** Integer micro-dollar estimate; unknown pricing is explicit rather than represented as a fabricated zero. */
public record ProviderCostEstimate(long estimatedCostMicros, boolean known) {
    public ProviderCostEstimate {
        if (estimatedCostMicros < 0 || (!known && estimatedCostMicros != 0)) throw new IllegalArgumentException("Provider cost estimate is invalid");
    }
    public static ProviderCostEstimate unknown() { return new ProviderCostEstimate(0, false); }
}
