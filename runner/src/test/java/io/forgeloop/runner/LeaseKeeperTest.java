package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LeaseKeeperTest {
    @Test void validatesSynchronouslyBeforeAnyProviderTurn() {
        AtomicBoolean lost = new AtomicBoolean();
        AtomicInteger calls = new AtomicInteger();
        try (var keeper = new LeaseKeeper(() -> { calls.incrementAndGet(); throw new Exception("cancelled"); }, lost, Duration.ofSeconds(30))) {
            assertTrue(lost.get());
            assertEquals(1, calls.get());
            keeper.renewNow();
            assertEquals(1, calls.get());
        }
    }

    @Test void backgroundRejectionStopsExecutionAndClosingDrainsRenewal() throws Exception {
        AtomicBoolean lost = new AtomicBoolean();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch rejected = new CountDownLatch(1);
        try (var keeper = new LeaseKeeper(() -> {
            if (calls.incrementAndGet() > 1) { rejected.countDown(); throw new Exception("expired"); }
        }, lost, Duration.ofMillis(10))) {
            assertTrue(rejected.await(2, TimeUnit.SECONDS));
        }
        assertTrue(lost.get());
        assertEquals(2, calls.get());
    }
}
