package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiGenerateContentProviderClientTest {
    @Test void parsesTextAndNativeUsage() throws Exception {
        ProviderResult result = GeminiGenerateContentProviderClient.parse("{\"responseId\":\"gem-1\",\"usageMetadata\":{\"promptTokenCount\":9,\"candidatesTokenCount\":5},\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"summary\\\":\\\"ok\\\"}\"}]}}]}");
        assertEquals(9, result.inputTokens()); assertEquals(5, result.outputTokens()); assertEquals("gem-1", result.providerRequestId());
    }
    @Test void rejectsEmptyOutput() { assertThrows(IllegalArgumentException.class, () -> GeminiGenerateContentProviderClient.parse("{\"candidates\":[]}")); }

    @Test void omitsJsonMimeTypeAndReplaysThoughtSignaturesAndFunctionResultsInOrder() throws Exception {
        ObjectMapper json = new ObjectMapper();
        var turn = GeminiGenerateContentProviderClient.parseConversation("{\"responseId\":\"gem-turn\",\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokenCount\":4,\"thoughtsTokenCount\":3},\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"name\":\"read_file\",\"args\":{\"path\":\"a\"}},\"thoughtSignature\":\"sig-gem\"}]}}]}");
        assertEquals(StopReason.TOOL_USE, turn.stopReason());
        assertEquals("g1-0", turn.toolCalls().get(0).id());
        assertEquals(7, turn.outputTokens());
        ConversationRequest request = new ConversationRequest("gemini-model", "rules", List.of(new UserText("inspect"), turn.toAssistantTurn(),
                new ToolResults(List.of(new ToolResultItem("g1-0", "read_file", "contents", false), new ToolResultItem("g1-1", "read_file", "blocked", true))),
                new UserText("continue")), List.of(new ToolSpec("read_file", "Read a file", json.readTree("{\"type\":\"object\"}"))), 1024, Duration.ofSeconds(45));
        var payload = json.readTree(GeminiGenerateContentProviderClient.conversationBody(request));
        assertFalse(payload.path("generationConfig").has("responseMimeType"));
        assertEquals("AUTO", payload.path("toolConfig").path("functionCallingConfig").path("mode").asText());
        assertEquals("sig-gem", payload.path("contents").get(1).path("parts").get(0).path("thoughtSignature").asText());
        var userReplyParts = payload.path("contents").get(2).path("parts");
        assertEquals("g1-0", userReplyParts.get(0).path("functionResponse").path("id").asText());
        assertEquals("contents", userReplyParts.get(0).path("functionResponse").path("response").path("result").asText());
        assertEquals("blocked", userReplyParts.get(1).path("functionResponse").path("response").path("error").asText());
        assertEquals("continue", userReplyParts.get(2).path("text").asText());
    }

    @Test void mapsStopReasonsAndMalformedCallsAreFailures() throws Exception {
        assertEquals(StopReason.MAX_TOKENS, finish("MAX_TOKENS"));
        assertEquals(StopReason.REFUSAL, finish("SAFETY"));
        assertEquals(StopReason.REFUSAL, finish("RECITATION"));
        assertEquals(StopReason.OTHER, finish("TOO_MANY_TOOL_CALLS"));
        assertEquals(StopReason.END_TURN, finish("STOP"));
        assertThrows(IllegalArgumentException.class, () -> GeminiGenerateContentProviderClient.parseConversation("{\"candidates\":[{\"finishReason\":\"MALFORMED_FUNCTION_CALL\",\"content\":{\"parts\":[]}}]}"));
        assertThrows(IllegalArgumentException.class, () -> GeminiGenerateContentProviderClient.parseConversation("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"read_file\",\"args\":\"bad\"}}]}}]}"));
    }

    private static StopReason finish(String reason) throws Exception {
        return GeminiGenerateContentProviderClient.parseConversation("{\"candidates\":[{\"finishReason\":\"" + reason + "\",\"content\":{\"parts\":[]}}]}").stopReason();
    }
}
