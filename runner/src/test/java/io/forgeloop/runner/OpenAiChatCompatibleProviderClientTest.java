package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class OpenAiChatCompatibleProviderClientTest {
    @Test void parsesChatCompletionAndUsage() throws Exception {
        ProviderResult result = OpenAiChatCompatibleProviderClient.parse("{\"id\":\"local-1\",\"usage\":{\"prompt_tokens\":4,\"completion_tokens\":3},\"choices\":[{\"message\":{\"content\":\"{}\"}}]}");
        assertEquals("{}", result.output()); assertEquals(4, result.inputTokens()); assertEquals(3, result.outputTokens());
    }
    @Test void rejectsEmptyOutput() { assertThrows(IllegalArgumentException.class, () -> OpenAiChatCompatibleProviderClient.parse("{\"choices\":[]}")); }
}
