package io.forgeloop.runner;

/** Final bounded failure retaining classification and local receipts; redact receipts before control telemetry. */
public final class ProviderExecutionFailure extends Exception {
    private final ProviderException providerFailure;
    private final int attemptCount;
    private final java.util.List<ProviderResult> receipts;
    public ProviderExecutionFailure(ProviderException providerFailure, int attemptCount) {
        this(providerFailure, attemptCount, providerFailure.receipt() == null ? java.util.List.of() : java.util.List.of(providerFailure.receipt()));
    }
    public ProviderExecutionFailure(ProviderException providerFailure, int attemptCount, java.util.List<ProviderResult> receipts) {
        super(providerFailure.getMessage(), providerFailure);
        this.receipts = java.util.List.copyOf(receipts);
        this.providerFailure = providerFailure; this.attemptCount = attemptCount;
    }
    public ProviderException providerFailure() { return providerFailure; }
    public java.util.List<ProviderResult> receipts() { return receipts; }
    public boolean usageKnown() { return receipts.size() == attemptCount; }
    public int attemptCount() { return attemptCount; }
}
