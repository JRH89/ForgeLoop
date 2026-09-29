package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SpendGovernorTest {
    private static final TurnCostBound BOUND = new TurnCostBound(100, 128, 50, true, 200);

    @Test
    void budgetRefusalIsAnOrdinaryDecision() {
        var decision = new SpendGovernor(duration -> { }).reserve(BOUND,
                micros -> new SpendReservationGrant(false, 0), new AtomicBoolean());

        assertEquals(SpendGovernor.Status.REFUSED, decision.status());
    }

    @Test
    void unknownCostNeverCallsControlPlane() {
        AtomicInteger calls = new AtomicInteger();
        TurnCostBound unknown = new TurnCostBound(100, 128, 0, false, 200);

        var decision = new SpendGovernor(duration -> { }).reserve(unknown, micros -> {
            calls.incrementAndGet(); return new SpendReservationGrant(true, micros);
        }, new AtomicBoolean());

        assertEquals(SpendGovernor.Status.NOT_REQUIRED, decision.status());
        assertEquals(0, calls.get());
    }

    @Test
    void retriesThreeTimesWithBackoffThenFailsClosed() {
        AtomicInteger calls = new AtomicInteger();
        List<Duration> delays = new ArrayList<>();
        var decision = new SpendGovernor(delays::add).reserve(BOUND, micros -> {
            calls.incrementAndGet(); throw new ControlPlaneFailure("unavailable", true);
        }, new AtomicBoolean());

        assertEquals(SpendGovernor.Status.UNAVAILABLE, decision.status());
        assertEquals(4, calls.get());
        assertEquals(List.of(Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8)), delays);
    }

    @Test
    void rejectedLeaseCallMarksLeaseLost() {
        AtomicBoolean leaseLost = new AtomicBoolean();
        var decision = new SpendGovernor(duration -> { }).reserve(BOUND,
                micros -> { throw new ControlPlaneFailure("lease rejected", false); }, leaseLost);

        assertEquals(SpendGovernor.Status.LEASE_LOST, decision.status());
        assertTrue(leaseLost.get());
    }

    @Test
    void grantMustExactlyMatchTheBoundAmount() {
        var decision = new SpendGovernor(duration -> { }).reserve(BOUND,
                micros -> new SpendReservationGrant(true, micros - 1), new AtomicBoolean());

        assertEquals(SpendGovernor.Status.UNAVAILABLE, decision.status());
        assertFalse(decision.status() == SpendGovernor.Status.GRANTED);
    }
}
