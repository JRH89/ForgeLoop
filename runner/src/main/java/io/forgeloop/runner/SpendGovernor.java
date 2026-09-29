package io.forgeloop.runner;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/** Obtains a fail-closed, idempotent reservation before a priced agent-loop turn is sent. */
public final class SpendGovernor {
    private static final Duration[] RETRY_DELAYS = {Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8)};
    private final Sleeper sleeper;

    public SpendGovernor() { this(duration -> Thread.sleep(duration.toMillis())); }

    SpendGovernor(Sleeper sleeper) { this.sleeper = java.util.Objects.requireNonNull(sleeper); }

    public Decision reserve(TurnCostBound bound, SpendReservationClient client, AtomicBoolean leaseLost) {
        if (bound == null || leaseLost == null) throw new IllegalArgumentException("Spend reservation inputs are required");
        if (!bound.costKnown()) return new Decision(Status.NOT_REQUIRED, 0);
        if (client == null) return new Decision(Status.UNAVAILABLE, 0);

        for (int attempt = 0; attempt <= RETRY_DELAYS.length; attempt++) {
            try {
                SpendReservationGrant grant = client.reserve(bound.reservedMicros());
                if (grant == null || grant.reservedMicros() < 0
                        || grant.granted() && grant.reservedMicros() != bound.reservedMicros()
                        || !grant.granted() && grant.reservedMicros() != 0)
                    return new Decision(Status.UNAVAILABLE, 0);
                return new Decision(grant.granted() ? Status.GRANTED : Status.REFUSED,
                        grant.granted() ? grant.reservedMicros() : 0);
            } catch (ControlPlaneFailure failure) {
                if (!failure.retryable()) {
                    leaseLost.set(true);
                    return new Decision(Status.LEASE_LOST, 0);
                }
            } catch (Exception transientFailure) {
                // The client deliberately exposes no raw exception details; never bypass a reservation on transport errors.
            }
            if (attempt == RETRY_DELAYS.length) break;
            try {
                sleeper.sleep(RETRY_DELAYS[attempt]);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return new Decision(Status.UNAVAILABLE, 0);
            }
        }
        // Keep diagnostics out of the result: transport exceptions can contain endpoint or credential details.
        return new Decision(Status.UNAVAILABLE, 0);
    }

    public enum Status { NOT_REQUIRED, GRANTED, REFUSED, LEASE_LOST, UNAVAILABLE }
    public record Decision(Status status, long reservedMicros) { }

    @FunctionalInterface interface Sleeper { void sleep(Duration duration) throws InterruptedException; }
}
