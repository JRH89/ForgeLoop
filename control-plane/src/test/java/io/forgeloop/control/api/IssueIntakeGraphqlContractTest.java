package io.forgeloop.control.api;

import static org.junit.jupiter.api.Assertions.*;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.execution.GraphQlSource;

/** Execute the actual schema/resolvers; mocks cannot catch missing response fields. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:intake-contract;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
    "forgeloop.security.mode=development", "forgeloop.security.development-organization-id=intake-contract"
})
class IssueIntakeGraphqlContractTest {
    @Autowired GraphQlSource source;
    @Autowired RepositoryConnectionRepository repositories;

    @Test void returnsLabelDefaultAndSupportsExplicitBypassThroughTheRealSchema() {
        repositories.save(new RepositoryConnection("intake-contract", "contract/repo", 1, "main", "ready", "GENERIC", List.of("unit"), 5));
        var initial = source.graphQl().execute("{ repositoryConnections { repository requireLabel requiredGates } }");
        assertTrue(initial.getErrors().isEmpty(), initial.getErrors().toString());
        assertTrue(initial.toSpecification().toString().contains("requireLabel=true"));
        var saved = source.graphQl().execute("""
            mutation { configureRepositoryIntake(repository: "contract/repo", requireAssignee: false, requireLabel: false) { requireLabel requiredGates } }
            """);
        assertTrue(saved.getErrors().isEmpty(), saved.getErrors().toString());
        assertTrue(saved.toSpecification().toString().contains("requireLabel=false"));
    }
}
