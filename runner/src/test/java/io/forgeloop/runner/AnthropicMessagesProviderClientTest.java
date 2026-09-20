package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class AnthropicMessagesProviderClientTest {
    @Test void parsesTextAndUsageWithoutTreatingToolBlocksAsOutput() throws Exception {
        ProviderResult result = AnthropicMessagesProviderClient.parse("{\"id\":\"msg_1\",\"usage\":{\"input_tokens\":11,\"output_tokens\":7},\"content\":[{\"type\":\"text\",\"text\":\"safe patch\"},{\"type\":\"tool_use\",\"name\":\"ignored\"}]}");
        assertEquals("safe patch", result.output()); assertEquals(11, result.inputTokens()); assertEquals(7, result.outputTokens());
    }
    @Test void rejectsAResponseWithoutText() { assertThrows(IllegalArgumentException.class, () -> AnthropicMessagesProviderClient.parse("{\"id\":\"msg_1\",\"usage\":{},\"content\":[]}")); }
}
