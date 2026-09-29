package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplayConversationClientTest {
    @TempDir Path temporary;
    private static final String RESPONSE = "{\"id\":\"resp-1\",\"model\":\"gpt-test\",\"status\":\"completed\",\"usage\":{\"input_tokens\":4,\"output_tokens\":2},\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"recorded answer\"}]}]}";

    @Test void replaysExactTurnWithTheRecordedRetryCount() throws Exception {
        StepJournal journal = new StepJournal(temporary, "turn-task", "lease-1", Clock.systemUTC());
        ConversationRequest request = request("inspect repository");
        journal.append("TURN_REQUESTED", requestRecord(request, 1, "lease-1/turn-1"));
        journal.append("TURN_COMPLETED", Map.of("turn", 1, "correlationId", "lease-1/turn-1", "attemptCount", 2,
                "responseBody", RESPONSE, "retryable", false));
        ReplayConversationClient replay = new ReplayConversationClient("openai", JournalFile.open(journal.path()));

        ConversationExecution execution = new ConversationExecutionService().converse(replay, request, 2);

        assertEquals(2, execution.attemptCount());
        assertEquals("recorded answer", execution.turn().text());
        assertEquals("openai-responses/1", replay.adapterId());
        assertThrows(ReplayDivergence.class, () -> replay.converse(request));
    }

    @Test void changedTurnDivergesAndFailedTurnPreservesTerminalRetryability() throws Exception {
        StepJournal journal = new StepJournal(temporary, "failed-turn", "lease-2", Clock.systemUTC());
        ConversationRequest request = request("inspect repository");
        journal.append("TURN_REQUESTED", requestRecord(request, 1, "lease-2/turn-1"));
        journal.append("TURN_FAILED", Map.of("turn", 1, "correlationId", "lease-2/turn-1", "attemptCount", 2, "retryable", false));
        ReplayConversationClient replay = new ReplayConversationClient("openai", JournalFile.open(journal.path()));
        ProviderExecutionFailure failure = assertThrows(ProviderExecutionFailure.class,
                () -> new ConversationExecutionService().converse(replay, request("changed request"), 2));
        assertEquals(1, failure.attemptCount());
        assertTrue(failure.providerFailure() instanceof ReplayDivergence);

        ReplayConversationClient retryReplay = new ReplayConversationClient("openai", JournalFile.open(journal.path()));
        ProviderExecutionFailure replayedFailure = assertThrows(ProviderExecutionFailure.class,
                () -> new ConversationExecutionService().converse(retryReplay, request, 3));
        assertEquals(2, replayedFailure.attemptCount());
        assertFalse(replayedFailure.providerFailure().retryable());
    }

    @Test void everyProviderUsesTheSameCredentialFreeConversationSerializerAndProductionParser() throws Exception {
        ProviderClientFactory codecs = new ProviderClientFactory();
        assertEquals("anthropic-messages/1", codecs.adapterId("anthropic"));
        assertEquals("openai-responses/1", codecs.adapterId("openai"));
        assertEquals("gemini-generate-content/1", codecs.adapterId("gemini"));
        assertEquals("openai-chat-compatible/1", codecs.adapterId("local"));
        assertTrue(codecs.conversationBody("anthropic", request("hello")).contains("hello"));
        assertTrue(codecs.conversationBody("gemini", request("hello")).contains("hello"));
        assertTrue(codecs.conversationBody("local", request("hello")).contains("hello"));
        assertThrows(IllegalArgumentException.class, () -> codecs.parseConversation("openai", "{}", request("hello")));
    }

    private static ConversationRequest request(String input) {
        return new ConversationRequest("gpt-test", "system rules", List.of(new UserText(input)), List.of(), 128, Duration.ofSeconds(10));
    }

    private static Map<String, Object> requestRecord(ConversationRequest request, int turn, String correlation) throws Exception {
        String body = new ProviderClientFactory().conversationBody("openai", request);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        return Map.of("turn", turn, "correlationId", correlation, "requestSha256", digest);
    }
}
