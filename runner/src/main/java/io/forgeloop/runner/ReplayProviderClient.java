package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Credential-free single-call replay driven by exact request digests and recorded provider response bodies. */
public final class ReplayProviderClient implements ProviderClient {
    private final String provider;
    private final ProviderClientFactory codecs;
    private final List<Call> calls;
    private int nextCall;

    public ReplayProviderClient(String provider, JournalFile journal) {
        if (provider == null || journal == null) throw new IllegalArgumentException("Replay provider inputs are incomplete");
        this.provider = provider;
        this.codecs = new ProviderClientFactory();
        codecs.adapterId(provider);
        this.calls = parseCalls(journal.records());
    }

    @Override public synchronized ProviderResult execute(ProviderRequest request) throws ProviderException {
        int number = nextCall + 1;
        if (nextCall >= calls.size()) throw new ReplayDivergence("Replay has no recorded response for call " + number);
        Call call = calls.get(nextCall);
        String body;
        try { body = codecs.requestBody(provider, request); }
        catch (Exception failure) { throw new ReplayDivergence("Replay diverged at call " + number + ": request could not be serialized"); }
        if (!Hashing.sha256(body.getBytes(StandardCharsets.UTF_8)).equals(call.requestSha256()))
            throw new ReplayDivergence("Replay diverged at call " + number + ": request digest differs");
        nextCall++;
        if (call.failure()) throw new ProviderException("Replayed recorded provider failure", call.retryable());
        if (call.responseBody() == null || call.responseBody().isBlank())
            throw new ReplayDivergence("Replay diverged at call " + number + ": recorded response body is unavailable");
        try { return codecs.parse(provider, call.responseBody()); }
        catch (Exception failure) { throw new ReplayDivergence("Replay diverged at call " + number + ": recorded response could not be parsed"); }
    }

    private static List<Call> parseCalls(List<JsonNode> records) {
        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            JsonNode requested = records.get(i);
            if (!"CALL_REQUESTED".equals(requested.path("type").asText())) continue;
            rejectAltered(requested);
            if (i + 1 >= records.size()) throw new IllegalArgumentException("Run journal ends after a provider request");
            JsonNode terminal = records.get(++i);
            rejectAltered(terminal);
            String terminalType = terminal.path("type").asText();
            if (!"CALL_COMPLETED".equals(terminalType) && !"CALL_FAILED".equals(terminalType))
                throw new IllegalArgumentException("Run journal provider request has no matching result");
            if (requested.path("call").asInt(-1) != terminal.path("call").asInt(-2)
                    || requested.path("try").asInt(-1) != terminal.path("try").asInt(-2))
                throw new IllegalArgumentException("Run journal provider request/result order is invalid");
            String digest = requested.path("requestSha256").asText("");
            if (!digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Run journal provider request digest is invalid");
            boolean failed = "CALL_FAILED".equals(terminalType);
            if (!failed && !terminal.hasNonNull("responseBody")) throw new IllegalArgumentException("Run journal provider response body is unavailable");
            calls.add(new Call(digest, failed, terminal.path("retryable").asBoolean(false),
                    failed ? null : terminal.path("responseBody").asText()));
        }
        return List.copyOf(calls);
    }

    private static void rejectAltered(JsonNode record) {
        if (record.has("redacted") || record.has("omitted") || record.has("originalSha256"))
            throw new IllegalArgumentException("Redacted or omitted run-journal records cannot be replayed");
    }

    private record Call(String requestSha256, boolean failure, boolean retryable, String responseBody) { }
}
