package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:agent-loop-policy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class AgentLoopPolicyPersistenceTest {
    @Autowired private EntityManager entityManager;

    @Test
    void repositoryPolicyAndRunSnapshotRoundTripIndependently() {
        AgentLoopBudget configured = new AgentLoopBudget(40, 200_000, 900, 1_048_576);
        String image = "node@sha256:" + "a".repeat(64);
        RepositoryConnection repository = new RepositoryConnection(
                "org", "owner/repository", 42L, "main", "forgeloop", "GENERIC",
                List.of(new VerificationPolicySpec("verify", "CONTAINER", image, List.of("npm", "test"),
                        "NONE", 300, true, "ALL")), 25.0, true);
        repository.configureAgentLoop(configured);
        repository.configureEnforcement(List.of("src/generated/**"), true, "verify");
        FeatureRun run = new FeatureRun("org", "owner/repository", "issue-4", "Title", "Spec", 5.0,
                "GENERIC", "main", repository.getPolicyRevision());
        run.adoptAgentLoop(repository.getAgentLoopBudget());
        run.snapshotEnforcement(repository.getEnforcement());

        entityManager.persist(repository);
        entityManager.persist(run);
        entityManager.flush();
        String repositoryId = repository.getId();
        String runId = run.getId();
        entityManager.clear();

        AgentLoopBudget storedRepositoryPolicy = entityManager.find(RepositoryConnection.class, repositoryId).getAgentLoopBudget();
        AgentLoopBudget storedRunSnapshot = entityManager.find(FeatureRun.class, runId).getAgentLoopBudget();
        assertNotNull(storedRepositoryPolicy);
        assertNotNull(storedRunSnapshot);
        assertEquals(40, storedRepositoryPolicy.getMaxToolCalls());
        assertEquals(200_000, storedRepositoryPolicy.getMaxTokens());
        assertEquals(900, storedRunSnapshot.getMaxWallSeconds());
        assertEquals(1_048_576, storedRunSnapshot.getMaxConversationBytes());
        assertEquals(new LoopEnforcement(List.of("src/generated/**"), true, "verify"),
                entityManager.find(FeatureRun.class, runId).getEnforcement());
        assertEquals(new LoopEnforcement(List.of("src/generated/**"), true, "verify"),
                entityManager.find(RepositoryConnection.class, repositoryId).getEnforcement());
    }
}
