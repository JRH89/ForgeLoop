package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.fasterxml.jackson.databind.ObjectMapper;
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
}
