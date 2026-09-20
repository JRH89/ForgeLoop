package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class OpenAiResponsesProviderClientTest {
    @Test void parsesOnlyTextOutputAndUsage() throws Exception {
        ProviderResult result = OpenAiResponsesProviderClient.parse("{\"id\":\"resp_1\",\"usage\":{\"input_tokens\":12,\"output_tokens\":8},\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"safe patch\"},{\"type\":\"refusal\",\"refusal\":\"ignored\"}]}]}");
        assertEquals("safe patch", result.output()); assertEquals(12, result.inputTokens()); assertEquals(8, result.outputTokens());
    }
    @Test void rejectsAnEmptyProviderOutput() {
        assertThrows(IllegalArgumentException.class, () -> OpenAiResponsesProviderClient.parse("{\"id\":\"resp_1\",\"usage\":{},\"output\":[]}"));
    }
}
