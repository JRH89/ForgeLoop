package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiResponsesProviderClientTest {
    @Test void parsesOnlyTextOutputAndUsage() throws Exception {
        ProviderResult result = OpenAiResponsesProviderClient.parse("{\"id\":\"resp_1\",\"usage\":{\"input_tokens\":12,\"output_tokens\":8},\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"safe patch\"},{\"type\":\"refusal\",\"refusal\":\"ignored\"}]}]}");
        assertEquals("safe patch", result.output()); assertEquals(12, result.inputTokens()); assertEquals(8, result.outputTokens());
    }
    @Test void rejectsAnEmptyProviderOutput() {
        assertThrows(IllegalArgumentException.class, () -> OpenAiResponsesProviderClient.parse("{\"id\":\"resp_1\",\"usage\":{},\"output\":[]}"));
    }

    @Test void declaresToolsReplaysReasoningAndSerializesCallOutputs() throws Exception {
        ObjectMapper json = new ObjectMapper();
        var response = OpenAiResponsesProviderClient.parseConversation("{\"id\":\"resp-tool\",\"status\":\"completed\",\"usage\":{\"input_tokens\":10,\"output_tokens\":4},\"output\":["
                + "{\"type\":\"reasoning\",\"encrypted_content\":\"secret-reasoning\"},"
                + "{\"type\":\"function_call\",\"call_id\":\"fc_1\",\"name\":\"read_file\",\"arguments\":\"{\\\"path\\\":\\\"README.md\\\"}\"}]}");
        assertEquals(StopReason.TOOL_USE, response.stopReason());
        assertEquals("README.md", response.toolCalls().get(0).arguments().path("path").asText());
        ConversationRequest request = new ConversationRequest("gpt", "rules", List.of(new UserText("inspect"), response.toAssistantTurn(),
                new ToolResults(List.of(new ToolResultItem("fc_1", "read_file", "text", false), new ToolResultItem("fc_2", "read_file", "denied", true)))),
                List.of(new ToolSpec("read_file", "Read a file", json.readTree("{\"type\":\"object\"}"))), 2048, Duration.ofSeconds(30));
        var payload = json.readTree(OpenAiResponsesProviderClient.conversationBody(request));
        assertEquals(false, payload.path("store").asBoolean());
        assertEquals("reasoning.encrypted_content", payload.path("include").get(0).asText());
        assertEquals("secret-reasoning", payload.path("input").get(1).path("encrypted_content").asText());
        assertEquals("function_call", payload.path("input").get(2).path("type").asText());
        assertEquals("text", payload.path("input").get(3).path("output").asText());
        assertEquals("ERROR: denied", payload.path("input").get(4).path("output").asText());
        assertEquals("read_file", payload.path("tools").get(0).path("name").asText());
        assertFalse(payload.has("text"));
    }

    @Test void mapsIncompleteAndRefusalStopsAndRejectsInvalidArguments() throws Exception {
        assertEquals(StopReason.MAX_TOKENS, OpenAiResponsesProviderClient.parseConversation("{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"},\"output\":[]}").stopReason());
        assertEquals(StopReason.REFUSAL, OpenAiResponsesProviderClient.parseConversation("{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"refusal\",\"refusal\":\"no\"}]}]}").stopReason());
        assertThrows(Exception.class, () -> OpenAiResponsesProviderClient.parseConversation("{\"status\":\"completed\",\"output\":[{\"type\":\"function_call\",\"call_id\":\"c\",\"name\":\"read_file\",\"arguments\":\"[]\"}]}"));
        assertTrue(OpenAiResponsesProviderClient.parseConversation("{\"status\":\"completed\",\"output\":[]}").stopReason() == StopReason.END_TURN);
    }
}
