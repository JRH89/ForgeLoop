package io.forgeloop.control.config;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bounds the cost of a single GraphQL request before any resolver runs. {@code /graphql} is reachable
 * without a session (resolvers enforce authorization), so an anonymous caller could otherwise send one
 * document that aliases an expensive field hundreds of times, or nests selections arbitrarily deep.
 *
 * <p>The defaults were measured against every operation the console, MCP gateway and runner send
 * (74 operations, October 2026): the deepest is 4 levels and the largest selects 79 fields. Complexity
 * uses graphql-java's default calculator, where every selected field, including each alias, costs one.
 * Raise the limits only if a legitimate client query outgrows them.</p>
 *
 * <p>Spring Boot registers every {@code Instrumentation} bean with the GraphQL engine.</p>
 */
@Configuration
public class GraphQlLimitsConfiguration {
    static final int DEFAULT_MAX_DEPTH = 10;
    static final int DEFAULT_MAX_COMPLEXITY = 300;

    @Bean
    MaxQueryDepthInstrumentation maxQueryDepthInstrumentation(
            @Value("${forgeloop.graphql.max-depth:" + DEFAULT_MAX_DEPTH + "}") int maxDepth) {
        return new MaxQueryDepthInstrumentation(requirePositive(maxDepth, "forgeloop.graphql.max-depth"));
    }

    @Bean
    MaxQueryComplexityInstrumentation maxQueryComplexityInstrumentation(
            @Value("${forgeloop.graphql.max-complexity:" + DEFAULT_MAX_COMPLEXITY + "}") int maxComplexity) {
        return new MaxQueryComplexityInstrumentation(requirePositive(maxComplexity, "forgeloop.graphql.max-complexity"));
    }

    private static int requirePositive(int value, String property) {
        if (value < 1) throw new IllegalStateException(property + " must be positive");
        return value;
    }
}
