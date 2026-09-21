package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class GeminiGenerateContentProviderClientTest {
    @Test void parsesTextAndNativeUsage() throws Exception {
        ProviderResult result = GeminiGenerateContentProviderClient.parse("{\"responseId\":\"gem-1\",\"usageMetadata\":{\"promptTokenCount\":9,\"candidatesTokenCount\":5},\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"summary\\\":\\\"ok\\\"}\"}]}}]}");
        assertEquals(9, result.inputTokens()); assertEquals(5, result.outputTokens()); assertEquals("gem-1", result.providerRequestId());
    }
    @Test void rejectsEmptyOutput() { assertThrows(IllegalArgumentException.class, () -> GeminiGenerateContentProviderClient.parse("{\"candidates\":[]}")); }
}
