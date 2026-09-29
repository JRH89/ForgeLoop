package io.forgeloop.control.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class GraphQlSchemaTest {
    @Test
    void schemaBuildsWithAgentLoopPolicyAndRunnerLifecycleOperations() throws IOException {
        String schemaSource;
        try (var input = new ClassPathResource("graphql/schema.graphqls").getInputStream()) {
            schemaSource = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse(schemaSource), RuntimeWiring.newRuntimeWiring().build());

        assertNotNull(schema.getMutationType().getFieldDefinition("configureRepositoryAgentLoop"));
        assertNotNull(schema.getMutationType().getFieldDefinition("configureRepositoryRunRecord"));
        assertNotNull(schema.getMutationType().getFieldDefinition("renewTaskLease"));
        assertNotNull(schema.getMutationType().getFieldDefinition("holdTaskLease"));
        assertNotNull(schema.getMutationType().getFieldDefinition("configureRepositoryEnforcement"));
        assertNotNull(schema.getMutationType().getFieldDefinition("completeTaskLease"));
        assertNotNull(schema.getObjectType("Task").getFieldDefinition("agentLoop"));
        assertNotNull(schema.getObjectType("Task").getFieldDefinition("redPrerequisite"));
        assertNotNull(schema.getObjectType("Task").getFieldDefinition("runRecord"));
        assertNotNull(schema.getObjectType("TaskAgentLoop").getFieldDefinition("enforcement"));
        assertNotNull(schema.getObjectType("RepositoryConnection").getFieldDefinition("agentLoop"));
        assertNotNull(schema.getObjectType("RepositoryConnection").getFieldDefinition("runRecord"));
        assertNotNull(schema.getObjectType("RepositoryConnection").getFieldDefinition("enforcement"));
        assertNotNull(schema.getObjectType("FeatureRun").getFieldDefinition("exitMeaning"));
        assertNotNull(schema.getObjectType("FeatureRun").getFieldDefinition("runRecord"));
        assertNotNull(schema.getObjectType("FeatureRun").getFieldDefinition("exitReason"));
    }
}
