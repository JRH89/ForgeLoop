package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProviderAnsweredModelTest {
    @Test void providerAdaptersRetainTheModelReportedByEachVendor() throws Exception {
        assertEquals("claude-actual", AnthropicMessagesProviderClient.parse(
                """
                {"model":"claude-actual","id":"msg-1","content":[{"type":"text","text":"ok"}],"usage":{"input_tokens":1,"output_tokens":1}}
                """).answeredModel());
        assertEquals("gpt-actual", OpenAiResponsesProviderClient.parse(
                """
                {"model":"gpt-actual","id":"resp-1","output":[{"content":[{"type":"output_text","text":"ok"}]}],"usage":{"input_tokens":1,"output_tokens":1}}
                """).answeredModel());
        assertEquals("compatible-actual", OpenAiChatCompatibleProviderClient.parse(
                """
                {"model":"compatible-actual","id":"chat-1","choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":1,"completion_tokens":1}}
                """).answeredModel());
        assertEquals("gemini-actual", GeminiGenerateContentProviderClient.parse(
                """
                {"modelVersion":"gemini-actual","responseId":"gem-1","candidates":[{"content":{"parts":[{"text":"ok"}]}}],"usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1}}
                """).answeredModel());
    }
}
