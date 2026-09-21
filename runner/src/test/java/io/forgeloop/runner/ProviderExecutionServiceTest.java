package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProviderExecutionServiceTest {
    private final ProviderRequest request = new ProviderRequest("model", "instructions", "input", 128);

    @Test void retriesTransientFailuresWithinTheBoundedBudget() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ProviderClient provider = ignored -> { if (calls.incrementAndGet() < 3) throw new ProviderException("unavailable", true); return new ProviderResult("{}", 1, 1, "request"); };
        assertEquals("{}", new ProviderExecutionService().execute(provider, request, 3).output());
        assertEquals(3, calls.get());
    }

    @Test void doesNotRetryPermanentProviderFailures() {
        AtomicInteger calls = new AtomicInteger();
        ProviderClient provider = ignored -> { calls.incrementAndGet(); throw new ProviderException("bad request", false); };
        assertThrows(ProviderException.class, () -> new ProviderExecutionService().execute(provider, request, 3));
        assertEquals(1, calls.get());
    }

    @Test void exposesTheExactAttemptCountWithoutProviderContent() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ProviderClient provider = ignored -> { if (calls.incrementAndGet() == 1) throw new ProviderException("outage", true); return new ProviderResult("{}", 1, 1, "request"); };
        assertEquals(2, new ProviderExecutionService().executeDetailed(provider, request, 3).attemptCount());
    }

    @Test void exhaustedTransientOutageProducesNoFalseResult() {
        AtomicInteger calls = new AtomicInteger();
        ProviderClient unavailable = ignored -> { calls.incrementAndGet(); throw new ProviderException("outage", true); };

        ProviderExecutionFailure failure = assertThrows(ProviderExecutionFailure.class,
                () -> new ProviderExecutionService().executeDetailed(unavailable, request, 3));

        assertEquals(3, calls.get());
        assertEquals(3, failure.attemptCount());
        assertEquals(true, failure.providerFailure().retryable());
    }
}
