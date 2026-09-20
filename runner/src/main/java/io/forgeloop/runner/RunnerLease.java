package io.forgeloop.runner;

/** One-time lease material retained locally between claim, acknowledgement, and completion. */
public record RunnerLease(String leaseId, String nonce) {
    public RunnerLease { if (leaseId == null || leaseId.isBlank() || nonce == null || nonce.isBlank()) throw new IllegalArgumentException("Lease material is incomplete"); }
}
