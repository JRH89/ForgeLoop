package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

/** Optional operator emergency cap, shared by runner processes using one ledger; separate from server run budgets. */
final class SpendLimitedProviderClient implements ProviderClient, ConversationClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ProviderExecutionPolicy policy;
    private final ProviderClient delegate;
    private final ProviderSpendLedger ledger;
    private final boolean invalid;

    private SpendLimitedProviderClient(ProviderExecutionPolicy policy, ProviderClient delegate, ProviderSpendLedger ledger, boolean invalid) {
        this.policy = policy; this.delegate = delegate; this.ledger = ledger; this.invalid = invalid;
    }

    static ProviderClient protect(ProviderExecutionPolicy policy, ProviderClient delegate, Map<String, String> environment) {
        String limit = environment.get("FORGELOOP_PROVIDER_SPEND_LIMIT_MICROS");
        String path = environment.get("FORGELOOP_PROVIDER_SPEND_LEDGER");
        if (limit == null && path == null) return delegate;
        try {
            if (limit == null || path == null || limit.isBlank() || path.isBlank() || policy.inputUsdPerMillion() == null)
                throw new IllegalArgumentException("Both cap settings and explicit policy prices are required");
            return new SpendLimitedProviderClient(policy, delegate, new ProviderSpendLedger(Path.of(path), Long.parseLong(limit)), false);
        } catch (Exception failure) { return new SpendLimitedProviderClient(policy, delegate, null, true); }
    }

    @Override public ProviderResult execute(ProviderRequest request) throws ProviderException {
        long reserved = reserve(request.model(), request.maxOutputTokens(), () -> new ProviderClientFactory().requestBody(policy.provider(), request));
        try {
            ProviderResult result = delegate.execute(request);
            settle(reserved, result.responseBody());
            return result;
        } catch (ProviderException failure) { settleFailure(reserved, failure); throw failure; }
    }

    @Override public String executeRaw(ProviderRequest request) throws ProviderException {
        long reserved = reserve(request.model(), request.maxOutputTokens(), () -> new ProviderClientFactory().requestBody(policy.provider(), request));
        try {
            String result = delegate.executeRaw(request);
            settle(reserved, result);
            return result;
        } catch (ProviderException failure) { settleFailure(reserved, failure); throw failure; }
    }

    @Override public ConversationTurn converse(ConversationRequest request) throws ProviderException {
        long reserved = reserve(request.model(), request.maxOutputTokens(), () -> serialize(request));
        try {
            ConversationTurn turn = conversation().converse(request);
            settle(reserved, turn.responseBody());
            return turn;
        } catch (ProviderException failure) { settleFailure(reserved, failure); throw failure; }
    }

    @Override public String serialize(ConversationRequest request) { return conversation().serialize(request); }
    @Override public String adapterId() { return conversation().adapterId(); }
    @Override public int serializerVersion() { return conversation().serializerVersion(); }
    private ConversationClient conversation() {
        if (delegate instanceof ConversationClient conversation) return conversation;
        throw new IllegalStateException("Provider does not support conversations");
    }

    private long reserve(String model, int output, Serialization body) throws ProviderException {
        if (invalid || !policy.model().equals(model)) throw configuration();
        try {
            // Every UTF-8 request byte is charged as a token, including the entire tool/schema/history payload.
            long bytes = body.get().getBytes(StandardCharsets.UTF_8).length;
            long reserved = new ProviderCostCalculator().fromTokens(policy, bytes, output).estimatedCostMicros();
            ledger.reserve(reserved);
            return reserved;
        } catch (ProviderException failure) { throw failure;
        } catch (Exception failure) { throw configuration(); }
    }

    private void settleFailure(long reserved, ProviderException failure) throws ProviderException {
        if (failure.receipt() == null) return;
        try { settle(reserved, failure.receipt().responseBody()); }
        catch (ProviderException ledgerFailure) { ledgerFailure.addSuppressed(failure); throw ledgerFailure; }
    }

    private void settle(long reserved, String body) throws ProviderException {
        // Unknown usage keeps the entire hold. Never infer a free attempt from missing vendor fields.
        if (body == null) return;
        try {
            JsonNode response = JSON.readTree(body);
            JsonNode usage = response.path(policy.provider().equals("gemini") ? "usageMetadata" : "usage");
            String inputKey = switch (policy.provider()) { case "gemini" -> "promptTokenCount"; case "local" -> "prompt_tokens"; default -> "input_tokens"; };
            String outputKey = switch (policy.provider()) { case "gemini" -> "candidatesTokenCount"; case "local" -> "completion_tokens"; default -> "output_tokens"; };
            if (!nonnegativeLong(usage.path(inputKey)) || !nonnegativeLong(usage.path(outputKey))) return;
            long output = usage.path(outputKey).asLong();
            if (policy.provider().equals("gemini") && usage.has("thoughtsTokenCount")) {
                if (!nonnegativeLong(usage.path("thoughtsTokenCount"))) return;
                output = Math.addExact(output, usage.path("thoughtsTokenCount").asLong());
            }
            long actual = new ProviderCostCalculator().fromTokens(policy, usage.path(inputKey).asLong(), output).estimatedCostMicros();
            ledger.settle(reserved, actual);
        } catch (ProviderException failure) { throw failure;
        } catch (Exception invalidReceipt) { /* Retain the conservative reservation when a receipt cannot be trusted. */ }
    }

    private static boolean nonnegativeLong(JsonNode value) { return value.isIntegralNumber() && value.canConvertToLong() && value.asLong() >= 0; }
    private static ProviderException configuration() { return new ProviderException("Runner provider spend cap requires a valid ledger and explicit matching model prices", false, null, null, "CONFIGURATION_ERROR", null); }
    @FunctionalInterface private interface Serialization { String get() throws Exception; }
}
