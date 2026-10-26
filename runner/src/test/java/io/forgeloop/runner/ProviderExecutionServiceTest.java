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
    @Test void rejectedBillableAttemptIsIncludedWhenRetrySucceedsWithoutChangingFinalResponse() throws Exception {
        var receipt = new ProviderResult("rejected", 100, 20, "first", "actual", "raw rejected");
        var finalResult = new ProviderResult("{}", 10, 2, "second", "actual", "raw final");
        var calls = new AtomicInteger();
        ProviderClient provider = ignored -> {
            if (calls.incrementAndGet() == 1) throw new ProviderException("rejected", true, null, null, "INVALID_OUTPUT", receipt);
            return finalResult;
        };
        var execution = new ProviderExecutionService().executeDetailed(provider, request, 2);
        var policy = new ProviderExecutionPolicy("anthropic", "model", 2, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN);
        var usage = ProviderUsageEvidence.from(policy, execution, new ProviderCostEstimate(30, true), "lease");
        assertEquals(110, usage.inputTokens()); assertEquals(22, usage.outputTokens());
        assertEquals(330, usage.estimatedCostMicros()); assertEquals(true, usage.costKnown());
        assertEquals("raw final", execution.result().responseBody());
    }

    @Test void transportFailureBeforeSuccessCannotBePricedAsKnown() throws Exception {
        var calls = new AtomicInteger();
        ProviderClient provider = ignored -> {
            if (calls.incrementAndGet() == 1) throw new ProviderException("transport", true);
            return new ProviderResult("ok", 10, 2, "second");
        };
        var execution = new ProviderExecutionService().executeDetailed(provider, request, 2);
        var usage = ProviderUsageEvidence.from(new ProviderExecutionPolicy("anthropic", "model", 2), execution,
                new ProviderCostEstimate(30, true), "lease");
        assertEquals(false, usage.costKnown()); assertEquals(0, usage.estimatedCostMicros());
        assertEquals(10, usage.inputTokens());
    }

    @Test void httpTokenExhaustionHasOneAttemptAndPricedRedactedFailure() throws Exception {
        String body = "{\"id\":\"msg-secret\",\"model\":\"actual\",\"stop_reason\":\"max_tokens\",\"usage\":{\"input_tokens\":31757,\"output_tokens\":8192},\"content\":[]}";
        var calls = new AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes(); byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var client = new AnthropicMessagesProviderClient(java.net.http.HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "offline-key");
            var failure = assertThrows(ProviderExecutionFailure.class, () -> new ProviderExecutionService().executeDetailed(client, request, 3));
            var policy = new ProviderExecutionPolicy("anthropic", "model", 3, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN);
            var report = ProviderAttemptReport.failed(ProviderFailureEvidence.from(policy, failure, "lease"));
            assertEquals(1, calls.get()); assertEquals("OUTPUT_TOKEN_LIMIT", report.category());
            assertEquals(false, report.retryable()); assertEquals(31757, report.inputTokens());
            assertEquals(8192, report.outputTokens()); assertEquals(113677, report.estimatedCostMicros());
            assertEquals(true, report.costKnown()); assertEquals("actual", report.answeredModel());
            assertEquals(false, report.toString().contains("msg-secret"));
            assertEquals(body, failure.providerFailure().receipt().responseBody());
        } finally { server.stop(0); }
    }
}
