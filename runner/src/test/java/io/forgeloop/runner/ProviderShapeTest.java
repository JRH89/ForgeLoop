package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProviderShapeTest {
    @Test void recordsOnlyAnthropicParserPathsAndKinds() {
        String response = "{\"id\":\"secret-id\",\"model\":\"claude-test\",\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"private response text\"}],\"usage\":{\"input_tokens\":3,\"output_tokens\":2}}";
        var shape = ProviderShape.of("anthropic", response);
        assertEquals("string", shape.kinds().get("content[].text"));
        assertEquals("number", shape.kinds().get("usage.input_tokens"));
        assertFalse(shape.kinds().toString().contains("private response text"));
        assertFalse(shape.kinds().toString().contains("secret-id"));
    }

    @Test void computesOpenAiResponsesShapeWithoutKeepingValues() {
        String response = "{\"id\":\"r1\",\"model\":\"gpt-x\",\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"private\"}]}],\"usage\":{\"input_tokens\":1,\"output_tokens\":2}}";
        var shape = ProviderShape.of("openai", response);
        assertEquals("array", shape.kinds().get("output"));
        assertEquals("string", shape.kinds().get("output[].content[].text"));
        assertEquals("missing", shape.kinds().get("output[].arguments"));
        assertFalse(shape.kinds().toString().contains("private"));
    }

    @Test void computesChatCompatibleAndGeminiShapes() {
        var chat = ProviderShape.of("local", "{\"id\":\"c1\",\"model\":\"local\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2}}");
        var gemini = ProviderShape.of("gemini", "{\"responseId\":\"g1\",\"modelVersion\":\"gemini-test\",\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"ok\"}]}}],\"usageMetadata\":{\"promptTokenCount\":1,\"candidatesTokenCount\":2}}");
        assertEquals("string", chat.kinds().get("choices[].message.content"));
        assertEquals("string", gemini.kinds().get("candidates[].content.parts[].text"));
        assertEquals("number", gemini.kinds().get("usageMetadata.promptTokenCount"));
    }

    @Test void driftComparisonIgnoresValuesAndReportsOnlyPathsAndKinds() {
        ProviderDriftCheck.Result same = new ProviderDriftCheck().compare("openai",
                "{\"id\":\"fixture\",\"model\":\"a\",\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"old content\"}]}],\"usage\":{\"input_tokens\":1,\"output_tokens\":2}}",
                "{\"id\":\"live\",\"model\":\"b\",\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"new content\"}]}],\"usage\":{\"input_tokens\":8,\"output_tokens\":9}}");
        assertTrue(same.matches());
        assertTrue(same.differences().isEmpty());
        ProviderDriftCheck.Result changed = new ProviderDriftCheck().compare("openai",
                "{\"output\":[],\"usage\":{\"input_tokens\":1}}", "{\"output\":[],\"usage\":{\"input_tokens\":\"one\"}}");
        assertFalse(changed.matches());
        assertTrue(changed.differences().contains("usage.input_tokens (expected number, got string)"));
        assertFalse(changed.differences().toString().contains("new content"));
        assertThrows(IllegalArgumentException.class, () -> ProviderShape.of("openai", "not-json"));
    }
}
