package io.forgeloop.control.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ControlPlaneGraphQlSchemaTest {
    @Test
    void schemaIncludingTestFirstFieldsBuilds() throws Exception {
        String schema = new ClassPathResource("graphql/schema.graphqls").getContentAsString(StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> new SchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse(schema), RuntimeWiring.newRuntimeWiring().build()));
    }
}
