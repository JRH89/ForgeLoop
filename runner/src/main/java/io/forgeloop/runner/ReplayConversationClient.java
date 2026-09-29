package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Replays agent-loop turns with the production provider serializer/parser and recorded retry semantics. */
public final class ReplayConversationClient implements ConversationClient {
    private final String provider;
    private final ProviderClientFactory codecs;
    private final List<Turn> turns;
    private int nextTurn;
    private int retriesRemaining;
    private boolean activeTurn;

    public ReplayConversationClient(String provider, JournalFile journal) {
        if (provider == null || journal == null) throw new IllegalArgumentException("Replay conversation inputs are incomplete");
        this.provider = provider;
        this.codecs = new ProviderClientFactory();
        this.codecs.adapterId(provider);
        this.turns = parseTurns(journal.records());
    }

    @Override public String adapterId() { return codecs.adapterId(provider); }

    @Override public String serialize(ConversationRequest request) { return codecs.conversationBody(provider, request); }

    @Override public synchronized ConversationTurn converse(ConversationRequest request) throws ProviderException {
        int number = nextTurn + 1;
        if (nextTurn >= turns.size()) throw new ReplayDivergence("Replay has no recorded response for call " + number);
        Turn turn = turns.get(nextTurn);
        String body;
        try { body = serialize(request); }
        catch (RuntimeException failure) { throw new ReplayDivergence("Replay diverged at call " + number + ": request could not be serialized"); }
        if (!Hashing.sha256(body.getBytes(StandardCharsets.UTF_8)).equals(turn.requestSha256()))
            throw new ReplayDivergence("Replay diverged at call " + number + ": request digest differs");
        if (!activeTurn) {
            retriesRemaining = turn.attemptCount() - 1;
            activeTurn = true;
        }
        if (retriesRemaining > 0) {
            retriesRemaining--;
            throw new ProviderException("Replayed recorded retry attempt", true);
        }
        nextTurn++;
        activeTurn = false;
        if (turn.failure()) throw new ProviderException("Replayed recorded conversation failure", turn.retryable());
        try { return codecs.parseConversation(provider, turn.responseBody(), request); }
        catch (Exception failure) { throw new ReplayDivergence("Replay diverged at call " + number + ": recorded response could not be parsed"); }
    }

    private static List<Turn> parseTurns(List<JsonNode> records) {
        List<Turn> turns = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            JsonNode requested = records.get(i);
            if (!"TURN_REQUESTED".equals(requested.path("type").asText())) continue;
            rejectAltered(requested);
            JsonNode terminal = null;
            for (int next = i + 1; next < records.size(); next++) {
                JsonNode possible = records.get(next);
                if ("TURN_COMPLETED".equals(possible.path("type").asText()) || "TURN_FAILED".equals(possible.path("type").asText())) {
                    terminal = possible;
                    i = next;
                    break;
                }
                if ("TURN_REQUESTED".equals(possible.path("type").asText())) break;
            }
            if (terminal == null) throw new IllegalArgumentException("Run journal conversation turn has no result");
            rejectAltered(terminal);
            if (!requested.path("correlationId").asText().equals(terminal.path("correlationId").asText())
                    || requested.path("turn").asInt(-1) != terminal.path("turn").asInt(-2))
                throw new IllegalArgumentException("Run journal conversation request/result order is invalid");
            String digest = requested.path("requestSha256").asText("");
            int attempts = terminal.path("attemptCount").asInt(-1);
            if (!digest.matches("[0-9a-f]{64}") || attempts < 1 || attempts > 3)
                throw new IllegalArgumentException("Run journal conversation request metadata is invalid");
            boolean failed = "TURN_FAILED".equals(terminal.path("type").asText());
            if (!failed && !terminal.hasNonNull("responseBody")) throw new IllegalArgumentException("Run journal conversation response body is unavailable");
            turns.add(new Turn(digest, attempts, failed, terminal.path("retryable").asBoolean(false),
                    failed ? null : terminal.path("responseBody").asText()));
        }
        return List.copyOf(turns);
    }

    private static void rejectAltered(JsonNode record) {
        if (record.has("redacted") || record.has("omitted") || record.has("originalSha256"))
            throw new IllegalArgumentException("Redacted or omitted run-journal records cannot be replayed");
    }

    private record Turn(String requestSha256, int attemptCount, boolean failure, boolean retryable, String responseBody) { }
}
