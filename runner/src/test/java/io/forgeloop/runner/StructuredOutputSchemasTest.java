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
}
