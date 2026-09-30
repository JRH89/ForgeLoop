package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnthropicMessagesProviderClientTest {
    @Test void parsesTextAndUsageWithoutTreatingToolBlocksAsOutput() throws Exception {
        ProviderResult result = AnthropicMessagesProviderClient.parse("{\"id\":\"msg_1\",\"usage\":{\"input_tokens\":11,\"output_tokens\":7},\"content\":[{\"type\":\"text\",\"text\":\"safe patch\"},{\"type\":\"tool_use\",\"name\":\"ignored\"}]}");
        assertEquals("safe patch", result.output()); assertEquals(11, result.inputTokens()); assertEquals(7, result.outputTokens());
    }
    @Test void rejectsAResponseWithoutText() { assertThrows(IllegalArgumentException.class, () -> AnthropicMessagesProviderClient.parse("{\"id\":\"msg_1\",\"usage\":{},\"content\":[]}")); }
    @Test void rejectsTruncatedOutputBeforeSchemaParsing() {
        assertThrows(IllegalArgumentException.class, () -> AnthropicMessagesProviderClient.parse("{\"id\":\"msg_1\",\"stop_reason\":\"max_tokens\",\"usage\":{},\"content\":[{\"type\":\"text\",\"text\":\"{}\"}]}"));
    }
    @Test void sendsSchemaThroughCurrentOutputConfigContract() throws Exception {
        String body = AnthropicMessagesProviderClient.requestBody(new ProviderRequest("claude", "instructions", "input", 128, StructuredOutputSchemas.plan()));
        var root = new ObjectMapper().readTree(body);
        assertEquals("json_schema", root.path("output_config").path("format").path("type").asText());
        assertTrue(root.path("output_config").path("format").path("schema").path("required").isArray());
        assertFalse(root.has("output_format"));
    }

    @Test void sendsRepositoryScanSchemaWithoutAnthropicUnsupportedArrayAndStringBounds() throws Exception {
        String body = AnthropicMessagesProviderClient.requestBody(new ProviderRequest("claude-sonnet-5", "instructions", "input", 6000,
                StructuredOutputSchemas.repositoryScan()));
        var schema = new ObjectMapper().readTree(body).path("output_config").path("format").path("schema");

        assertNull(schema.findValue("maxItems"));
        assertNull(schema.findValue("maxLength"));
    }

    @Test void serializesToolCallsWithVendorReplayAndToolResultsBeforeFollowingText() throws Exception {
        ObjectMapper json = new ObjectMapper();
        var schema = json.readTree("{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}");
        var replay = json.readTree("[{\"type\":\"thinking\",\"thinking\":\"private\",\"signature\":\"sig-1\"},{\"type\":\"tool_use\",\"id\":\"call-1\",\"name\":\"read_file\",\"input\":{\"path\":\"README.md\"}}]");
        ConversationRequest request = new ConversationRequest("claude", "rules", List.of(new UserText("inspect"),
                new AssistantTurn("", List.of(new ToolCall("call-1", "read_file", json.readTree("{\"path\":\"README.md\"}"))), replay),
                new ToolResults(List.of(new ToolResultItem("call-1", "read_file", "contents", false))), new UserText("continue")),
                List.of(new ToolSpec("read_file", "Read a file", schema)), 1024, Duration.ofMinutes(2));
        var payload = json.readTree(AnthropicMessagesProviderClient.conversationBody(request));
        assertEquals("sig-1", payload.path("messages").get(1).path("content").get(0).path("signature").asText());
        assertEquals("tool_use", payload.path("messages").get(1).path("content").get(1).path("type").asText());
        var merged = payload.path("messages").get(2).path("content");
        assertEquals("tool_result", merged.get(0).path("type").asText());
        assertEquals("text", merged.get(1).path("type").asText());
        assertEquals("continue", merged.get(1).path("text").asText());
        assertEquals("read_file", payload.path("tools").get(0).path("name").asText());
        assertFalse(payload.has("tool_choice"));
        assertFalse(payload.has("output_config"));
    }

    @Test void acceptsToolOnlyTurnsMapsStopReasonsAndRejectsNonObjectArguments() throws Exception {
        var turn = AnthropicMessagesProviderClient.parseConversation("{\"id\":\"msg-tool\",\"stop_reason\":\"tool_use\",\"usage\":{\"input_tokens\":8,\"output_tokens\":4},\"content\":[{\"type\":\"tool_use\",\"id\":\"call-2\",\"name\":\"read_file\",\"input\":{\"path\":\"x\"}}]}");
        assertEquals("", turn.text());
        assertEquals(StopReason.TOOL_USE, turn.stopReason());
        assertEquals("x", turn.toolCalls().get(0).arguments().path("path").asText());
        assertEquals(8, turn.inputTokens());
        assertEquals("tool_use", turn.toAssistantTurn().replay().get(0).path("type").asText());
        assertEquals(StopReason.END_TURN, reason("end_turn"));
        assertEquals(StopReason.MAX_TOKENS, reason("model_context_window_exceeded"));
        assertEquals(StopReason.REFUSAL, reason("refusal"));
        assertEquals(StopReason.OTHER, reason("pause_turn"));
        assertThrows(IllegalArgumentException.class, () -> AnthropicMessagesProviderClient.parseConversation(
                "{\"content\":[{\"type\":\"tool_use\",\"id\":\"c\",\"name\":\"read_file\",\"input\":\"bad\"}] }"));
    }

    private static StopReason reason(String stopReason) throws Exception {
        return AnthropicMessagesProviderClient.parseConversation("{\"content\":[],\"stop_reason\":\"" + stopReason + "\"}").stopReason();
    }
}
