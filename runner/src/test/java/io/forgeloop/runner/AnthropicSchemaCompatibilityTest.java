package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AnthropicSchemaCompatibilityTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void removesUnsupportedBoundsRecursivelyWithoutMutatingSharedSchema() throws Exception {
        JsonNode source = json.readTree("""
                {"type":"object","properties":{"findings":{"type":"array","maxItems":12,"minItems":1,
                  "items":{"type":"object","properties":{"title":{"type":"string","maxLength":200},
                  "score":{"type":"number","minimum":0,"maximum":1,"multipleOf":0.1}}}}}}
                """);

        JsonNode normalized = AnthropicSchemaCompatibility.normalize(source);
        JsonNode findings = normalized.path("properties").path("findings");
        JsonNode title = findings.path("items").path("properties").path("title");
        JsonNode score = findings.path("items").path("properties").path("score");

        assertNull(findings.get("maxItems"));
        assertEquals(1, findings.path("minItems").asInt());
        assertNull(title.get("maxLength"));
        assertNull(score.get("minimum"));
        assertNull(score.get("maximum"));
        assertNull(score.get("multipleOf"));
        assertEquals(12, source.path("properties").path("findings").path("maxItems").asInt());
    }

    @Test void removesUnsupportedMinItemsValuesButKeepsProviderSupportedValues() throws Exception {
        JsonNode source = json.readTree("""
                {"type":"object","properties":{"invalid":{"type":"array","minItems":2},
                  "valid":{"type":"array","minItems":0}}}
                """);

        JsonNode normalized = AnthropicSchemaCompatibility.normalize(source);

        assertFalse(normalized.path("properties").path("invalid").has("minItems"));
        assertEquals(0, normalized.path("properties").path("valid").path("minItems").asInt());
    }
}
