package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

class StructuredOutputSchemasTest {
    @Test void workerSchemasAreStrictObjectsWithRequiredFields() {
        assertEquals("object", StructuredOutputSchemas.plan().path("type").asText());
        assertFalse(StructuredOutputSchemas.plan().path("additionalProperties").asBoolean());
        assertEquals(2, StructuredOutputSchemas.plan().path("required").size());
        assertEquals(2, StructuredOutputSchemas.patch().path("required").size());
        assertEquals(3, StructuredOutputSchemas.review().path("required").size());
    }

    @Test void callersCannotMutateSharedSchemas() {
        var schema = (com.fasterxml.jackson.databind.node.ObjectNode) StructuredOutputSchemas.plan();
        schema.remove("required");
        assertEquals(2, StructuredOutputSchemas.plan().path("required").size());
    }
    @Test void patchSchemaConstrainsMetadataAndTaskOwnedPrefixesBeforeGeneration() {
        var schema = StructuredOutputSchemas.patch(java.util.List.of("src/Feature.js", "README.md"));
        var properties = schema.path("properties");
        assertEquals(500, properties.path("summary").path("maxLength").asInt());
        assertFalse(properties.path("summary").has("pattern"));
        String paths = properties.path("changes").path("items").path("properties").path("path").path("pattern").asText();
        org.junit.jupiter.api.Assertions.assertTrue("src/Feature.js".matches(paths));
        org.junit.jupiter.api.Assertions.assertTrue("README.md".matches(paths));
        assertFalse("tests/Feature.test.js".matches(paths));
        assertFalse("src/Feature.js.bak".matches(paths));
        var normalized = AnthropicSchemaCompatibility.normalize(schema);
        assertFalse(normalized.path("properties").path("summary").has("maxLength"));
        org.junit.jupiter.api.Assertions.assertTrue(normalized.path("properties").path("summary").path("description").asText().contains("500"));
        assertEquals(paths, normalized.path("properties").path("changes").path("items").path("properties").path("path").path("pattern").asText());
        assertFalse(StructuredOutputSchemas.patch().path("properties").path("changes").path("items").path("properties").path("path").has("pattern"));
    }

    @Test void scopeSchemaNormalizesDirectoryPrefixesWithoutExpandingThem() {
        String pattern = StructuredOutputSchemas.patch(java.util.List.of("./src/", "docs")).path("properties")
                .path("changes").path("items").path("properties").path("path").path("pattern").asText();
        org.junit.jupiter.api.Assertions.assertTrue("src/Feature.js".matches(pattern));
        org.junit.jupiter.api.Assertions.assertTrue("docs/guide.md".matches(pattern));
        assertFalse("src-other/Feature.js".matches(pattern));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> StructuredOutputSchemas.patch(java.util.List.of("../outside")));
    }

    @Test void removedVendorBoundsRemainInDescriptions() throws Exception {
        var schema = new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"type\":\"string\",\"description\":\"Human summary\",\"maxLength\":500}");
        var normalized = AnthropicSchemaCompatibility.normalize(schema);
        assertFalse(normalized.has("maxLength"));
        org.junit.jupiter.api.Assertions.assertTrue(normalized.path("description").asText().contains("500"));
        org.junit.jupiter.api.Assertions.assertTrue(normalized.path("description").asText().contains("Human summary"));
        org.junit.jupiter.api.Assertions.assertTrue(schema.has("maxLength"));
    }
    @Test void rootScopeDoesNotAddASecondFilenamePolicy() {
        String pattern = StructuredOutputSchemas.patch(java.util.List.of(".")).path("properties")
                .path("changes").path("items").path("properties").path("path").path("pattern").asText();
        org.junit.jupiter.api.Assertions.assertTrue("README.md".matches(pattern));
        org.junit.jupiter.api.Assertions.assertTrue("docs/User Guide.md".matches(pattern));
        assertFalse("".matches(pattern));
    }
}
