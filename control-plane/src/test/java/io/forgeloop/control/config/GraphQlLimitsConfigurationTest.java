package io.forgeloop.control.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import graphql.GraphQL;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphQlLimitsConfigurationTest {
    private final GraphQlLimitsConfiguration configuration = new GraphQlLimitsConfiguration();
    /** A self-referencing type is the shape that makes unbounded nesting possible. */
    private final GraphQL graphQl = GraphQL.newGraphQL(new SchemaGenerator().makeExecutableSchema(
                    new SchemaParser().parse("type Query { node: Node } type Node { id: ID child: Node }"), RuntimeWiring.newRuntimeWiring().build()))
            .instrumentation(new ChainedInstrumentation(List.of(
                    configuration.maxQueryDepthInstrumentation(GraphQlLimitsConfiguration.DEFAULT_MAX_DEPTH),
                    configuration.maxQueryComplexityInstrumentation(GraphQlLimitsConfiguration.DEFAULT_MAX_COMPLEXITY))))
            .build();

    @Test void acceptsNestingWithinTheLimitAndRejectsDeeperDocuments() {
        assertTrue(graphQl.execute(nested(GraphQlLimitsConfiguration.DEFAULT_MAX_DEPTH)).getErrors().isEmpty());
        var tooDeep = graphQl.execute(nested(GraphQlLimitsConfiguration.DEFAULT_MAX_DEPTH + 1));
        assertFalse(tooDeep.getErrors().isEmpty());
        assertTrue(tooDeep.getErrors().toString().toLowerCase().contains("depth"), tooDeep.getErrors().toString());
    }

    @Test void refusesNonPositiveLimits() {
        assertThrows(IllegalStateException.class, () -> configuration.maxQueryDepthInstrumentation(0));
        assertThrows(IllegalStateException.class, () -> configuration.maxQueryComplexityInstrumentation(-1));
    }

    /** Builds {@code { node { child { ... { id } } } }} whose deepest field sits at {@code depth}. */
    private static String nested(int depth) {
        return "{ node { " + "child { ".repeat(depth - 2) + "id" + " }".repeat(depth - 2) + " } }";
    }
}
