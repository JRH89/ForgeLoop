package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiChatCompatibleProviderClientTest {
    @Test void parsesChatCompletionAndUsage() throws Exception {
        ProviderResult result = OpenAiChatCompatibleProviderClient.parse("{\"id\":\"local-1\",\"usage\":{\"prompt_tokens\":4,\"completion_tokens\":3},\"choices\":[{\"message\":{\"content\":\"{}\"}}]}");
        assertEquals("{}", result.output()); assertEquals(4, result.inputTokens()); assertEquals(3, result.outputTokens());
    }
    @Test void rejectsEmptyOutput() { assertThrows(IllegalArgumentException.class, () -> OpenAiChatCompatibleProviderClient.parse("{\"choices\":[]}")); }

    @Test void supportsNullTextToolCallsAndReplaysAssistantMessageBeforeToolResults() throws Exception {
        ObjectMapper json = new ObjectMapper();
        var turn = OpenAiChatCompatibleProviderClient.parseConversation("{\"id\":\"local-tool\",\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":5},\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"read_file\",\"arguments\":\"{\\\"path\\\":\\\"a\\\"}\"}}]}}]}");
        assertEquals("", turn.text());
        assertEquals(StopReason.TOOL_USE, turn.stopReason());
        assertEquals(12, turn.inputTokens());
        ConversationRequest request = new ConversationRequest("qwen", "rules", List.of(new UserText("inspect"), turn.toAssistantTurn(),
                new ToolResults(List.of(new ToolResultItem("call_1", "read_file", "data", true)))),
                List.of(new ToolSpec("read_file", "Read", json.readTree("{\"type\":\"object\"}"))), 512, Duration.ofSeconds(20));
        var payload = json.readTree(OpenAiChatCompatibleProviderClient.conversationBody(request));
        assertEquals("assistant", payload.path("messages").get(2).path("role").asText());
        assertEquals("call_1", payload.path("messages").get(2).path("tool_calls").get(0).path("id").asText());
        assertEquals("tool", payload.path("messages").get(3).path("role").asText());
        assertEquals("ERROR: data", payload.path("messages").get(3).path("content").asText());
        assertEquals("read_file", payload.path("tools").get(0).path("function").path("name").asText());
        assertFalse(payload.has("response_format"));
    }

    @Test void mapsFinishReasonsAndRejectsMalformedToolArguments() throws Exception {
        assertEquals(StopReason.MAX_TOKENS, finish("length"));
        assertEquals(StopReason.REFUSAL, finish("content_filter"));
        assertEquals(StopReason.END_TURN, finish("stop"));
        assertEquals(StopReason.OTHER, finish("unknown"));
        assertThrows(Exception.class, () -> OpenAiChatCompatibleProviderClient.parseConversation("{\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":{\"tool_calls\":[{\"id\":\"c\",\"function\":{\"name\":\"read_file\",\"arguments\":\"[]\"}}]}}]}"));
    }

    private static StopReason finish(String reason) throws Exception {
        return OpenAiChatCompatibleProviderClient.parseConversation("{\"choices\":[{\"finish_reason\":\"" + reason + "\",\"message\":{\"content\":\"ok\"}}]}").stopReason();
    }
}
