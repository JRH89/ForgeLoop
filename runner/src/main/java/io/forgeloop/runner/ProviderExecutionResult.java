package io.forgeloop.runner;

import java.util.List;

/** Final response remains exact for replay; receipts retain billable rejected attempts separately. */
public record ProviderExecutionResult(ProviderResult result, int attemptCount, List<ProviderResult> receipts) {
    public ProviderExecutionResult(ProviderResult result, int attemptCount) { this(result, attemptCount, List.of(result)); }
    public ProviderExecutionResult {
        if (result == null || attemptCount < 1 || attemptCount > 3) throw new IllegalArgumentException("Provider execution result is invalid");
        receipts = List.copyOf(receipts);
    }
    public boolean usageKnown() { return receipts.size() == attemptCount; }
}
