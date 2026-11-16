package io.forgeloop.control.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.execution.GraphQlSource;

/** Runs the request-cost limits through the real engine, using the same context as the intake contract test. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:intake-contract;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
    "forgeloop.security.mode=development", "forgeloop.security.development-organization-id=intake-contract"
})
class GraphQlLimitsContractTest {
    @Autowired GraphQlSource source;

    @Test void theDeepestConsoleQueryShapeIsStillAccepted() {
        var result = source.graphQl().execute("""
            { featureRuns(page: 0, size: 50) { id state tasks { id state providerAttempts { id provider model costKnown } }
              gates { name state } criteria { id statement } publication { pullRequestNumber } } }
            """);
        assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
    }

    @Test void aliasFloodsAreRejectedBeforeAnyResolverRuns() {
        String aliases = IntStream.range(0, 400).mapToObj(index -> "a" + index + ": subject").collect(Collectors.joining(" "));
        var result = source.graphQl().execute("{ currentOperator { " + aliases + " } }");
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().toString().toLowerCase().contains("complexity"), result.getErrors().toString());
        assertTrue(result.getData() == null || result.toSpecification().get("data") == null);
    }

    @Test void schemaIntrospectionIsDisabledByDefault() {
        var result = source.graphQl().execute("{ __schema { queryType { name } } }");
        assertFalse(result.getErrors().isEmpty(), "introspection must not reveal the schema");
    }
}
