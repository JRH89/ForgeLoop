package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@org.junit.jupiter.api.condition.EnabledOnOs({org.junit.jupiter.api.condition.OS.LINUX, org.junit.jupiter.api.condition.OS.MAC})
class SpendLimitedProviderClientTest {
    @TempDir Path directory;
    private final ProviderExecutionPolicy policy = new ProviderExecutionPolicy("anthropic", "model", 1, BigDecimal.ONE, BigDecimal.ONE);
    private final ProviderRequest request = new ProviderRequest("model", "rules", "input", 128);
    private Map<String, String> environment(long cap) {
        return Map.of("FORGELOOP_PROVIDER_SPEND_LIMIT_MICROS", Long.toString(cap), "FORGELOOP_PROVIDER_SPEND_LEDGER", directory.resolve("ledger").toString());
    }
    @Test void capRefusalNeverCallsDelegate() throws Exception {
        var calls = new AtomicInteger();
        ProviderClient delegate = ignored -> { calls.incrementAndGet(); return new ProviderResult("ok", 1, 1, "id"); };
        var client = SpendLimitedProviderClient.protect(policy, delegate, environment(1));
        var failure = assertThrows(ProviderException.class, () -> client.execute(request));
        assertEquals("BUDGET_EXHAUSTED", failure.category()); assertFalse(failure.retryable()); assertEquals(0, calls.get());
    }
    @Test void transportReservationSurvivesClientRecreation() throws Exception {
        long bound = new ProviderClientFactory().requestBody("anthropic", request).getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 128;
        ProviderClient delegate = ignored -> { throw new ProviderException("transport", true); };
        assertThrows(ProviderException.class, () -> SpendLimitedProviderClient.protect(policy, delegate, environment(bound)).execute(request));
        var recreated = SpendLimitedProviderClient.protect(policy, delegate, environment(bound));
        assertEquals("BUDGET_EXHAUSTED", assertThrows(ProviderException.class, () -> recreated.execute(request)).category());
    }
    @Test void configurationFailuresNeverCallDelegate() {
        ProviderClient delegate = ignored -> { fail("must not send"); return null; };
        var invalid = SpendLimitedProviderClient.protect(policy, delegate, Map.of("FORGELOOP_PROVIDER_SPEND_LIMIT_MICROS", "20"));
        assertEquals("CONFIGURATION_ERROR", assertThrows(ProviderException.class, () -> invalid.execute(request)).category());
    }
    private long bound() throws Exception {
        return new ProviderClientFactory().requestBody("anthropic", request).getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 128;
    }
    private String receipt(long input, long output) { return "{\"usage\":{\"input_tokens\":" + input + ",\"output_tokens\":" + output + "}}"; }
    @Test void actualReceiptSettlesAndLedgerIsPrivate() throws Exception {
        ProviderClient delegate = ignored -> new ProviderResult("ok", 1, 2, "id", "model", receipt(1, 2));
        var client = SpendLimitedProviderClient.protect(policy, delegate, environment(bound() + 3));
        client.execute(request); client.execute(request);
        assertEquals("6\n", java.nio.file.Files.readString(directory.resolve("ledger")));
        assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"), java.nio.file.Files.getPosixFilePermissions(directory.resolve("ledger")));
        assertFalse(java.nio.file.Files.readString(directory.resolve("ledger")).contains("input_tokens"));
    }
    @Test void missingReceiptRetainsHoldEvenWhenAdapterReturnsZeroTokens() throws Exception {
        ProviderClient delegate = ignored -> new ProviderResult("ok", 0, 0, "id", "model", "{}");
        var client = SpendLimitedProviderClient.protect(policy, delegate, environment(bound()));
        client.execute(request);
        assertEquals("BUDGET_EXHAUSTED", assertThrows(ProviderException.class, () -> client.execute(request)).category());
    }
    @Test void failedKnownReceiptSettlesBillableCharge() throws Exception {
        ProviderClient delegate = ignored -> { throw new ProviderException("limit", false, null, null, "OUTPUT_TOKEN_LIMIT",
                new ProviderResult("partial", 1, 2, "id", "model", receipt(1, 2))); };
        var client = SpendLimitedProviderClient.protect(policy, delegate, environment(bound()));
        assertThrows(ProviderException.class, () -> client.execute(request));
        assertEquals("3\n", java.nio.file.Files.readString(directory.resolve("ledger")));
    }
    @Test void concurrentSiblingClientsCannotOverspend() throws Exception {
        var entered = new AtomicInteger();
        var release = new java.util.concurrent.CountDownLatch(1);
        ProviderClient delegate = ignored -> {
            entered.incrementAndGet();
            try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            throw new ProviderException("transport", true);
        };
        var first = SpendLimitedProviderClient.protect(policy, delegate, environment(bound()));
        var second = SpendLimitedProviderClient.protect(policy, delegate, environment(bound()));
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var pending = executor.submit(() -> { try { first.execute(request); } catch (ProviderException ignored) {} });
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
            while (entered.get() == 0 && System.nanoTime() < deadline) Thread.onSpinWait();
            try {
                assertEquals(1, entered.get());
                assertEquals("BUDGET_EXHAUSTED", assertThrows(ProviderException.class, () -> second.execute(request)).category());
                assertEquals(1, entered.get());
            } finally { release.countDown(); }
            pending.get();
        }
    }
    @Test void invalidPricesAndCorruptOrUnsafeLedgerFailBeforeSend() throws Exception {
        ProviderClient delegate = ignored -> { fail("must not send"); return null; };
        var missingPrices = SpendLimitedProviderClient.protect(new ProviderExecutionPolicy("anthropic", "model", 1), delegate, environment(10000));
        assertEquals("CONFIGURATION_ERROR", assertThrows(ProviderException.class, () -> missingPrices.execute(request)).category());
        var ledger = directory.resolve("ledger"); java.nio.file.Files.writeString(ledger, "corrupt");
        java.nio.file.Files.setPosixFilePermissions(ledger, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        var client = SpendLimitedProviderClient.protect(policy, delegate, environment(10000));
        assertEquals("CONFIGURATION_ERROR", assertThrows(ProviderException.class, () -> client.execute(request)).category());
        java.nio.file.Files.writeString(ledger, "0\n");
        java.nio.file.Files.setPosixFilePermissions(ledger, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
        assertEquals("CONFIGURATION_ERROR", assertThrows(ProviderException.class, () -> client.execute(request)).category());
    }
    @Test void rawExecutionCannotBypassTheCap() throws Exception {
        ProviderClient delegate = ignored -> { fail("must not send"); return null; };
        var client = SpendLimitedProviderClient.protect(policy, delegate, environment(1));
        assertEquals("BUDGET_EXHAUSTED", assertThrows(ProviderException.class, () -> client.executeRaw(request)).category());
    }
    @Test void conversationContractIsPreservedAndReserved() throws Exception {
        class Conversational implements ProviderClient, ConversationClient {
            public ProviderResult execute(ProviderRequest ignored) { throw new AssertionError(); }
            public String serialize(ConversationRequest ignored) { return "exact serialized conversation"; }
            public String adapterId() { return "fixture-adapter"; }
            public int serializerVersion() { return 7; }
            public ConversationTurn converse(ConversationRequest ignored) {
                return new ConversationTurn("ok", java.util.List.of(), StopReason.END_TURN, 1, 2, "id", new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode(), receipt(1,2));
            }
        }
        var delegate = new Conversational();
        var client = (ConversationClient) SpendLimitedProviderClient.protect(policy, delegate, environment(1000));
        var turnRequest = new ConversationRequest("model", "rules", java.util.List.of(new UserText("input")), java.util.List.of(), 128, java.time.Duration.ofSeconds(5));
        assertEquals(delegate.serialize(turnRequest), client.serialize(turnRequest));
        assertEquals("fixture-adapter", client.adapterId()); assertEquals(7, client.serializerVersion());
        assertEquals("ok", client.converse(turnRequest).text());
        assertEquals("3\n", java.nio.file.Files.readString(directory.resolve("ledger")));
        var refused = (ConversationClient) SpendLimitedProviderClient.protect(policy, delegate, environment(1));
        assertEquals("BUDGET_EXHAUSTED", assertThrows(ProviderException.class, () -> refused.converse(turnRequest)).category());
    }
    @Test void rawKnownReceiptSettlesWithoutChangingTheResponse() throws Exception {
        String raw = receipt(2, 3);
        ProviderClient delegate = new ProviderClient() {
            public ProviderResult execute(ProviderRequest ignored) { throw new AssertionError("raw path required"); }
            public String executeRaw(ProviderRequest ignored) { return raw; }
        };
        var client = SpendLimitedProviderClient.protect(policy, delegate, environment(bound()));
        assertEquals(raw, client.executeRaw(request));
        assertEquals("5\n", java.nio.file.Files.readString(directory.resolve("ledger")));
    }
    @Test void abruptDelegateFailureLeavesDurableReservation() throws Exception {
        ProviderClient delegate = ignored -> { throw new AssertionError("simulated VM interruption after reserve"); };
        assertThrows(AssertionError.class, () -> SpendLimitedProviderClient.protect(policy, delegate, environment(bound())).execute(request));
        var recreated = SpendLimitedProviderClient.protect(policy, delegate, environment(bound()));
        assertEquals("BUDGET_EXHAUSTED", assertThrows(ProviderException.class, () -> recreated.execute(request)).category());
    }
    @Test void unsetCapLeavesTheOriginalClientUntouched() {
        ProviderClient delegate = ignored -> new ProviderResult("ok", 1, 1, "id");
        assertSame(delegate, SpendLimitedProviderClient.protect(policy, delegate, Map.of()));
    }
}
