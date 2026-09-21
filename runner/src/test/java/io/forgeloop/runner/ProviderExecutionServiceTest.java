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
}
