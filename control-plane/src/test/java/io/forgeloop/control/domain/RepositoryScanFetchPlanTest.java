package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import io.forgeloop.control.application.RepositoryScanService;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import io.forgeloop.control.security.OperatorContext;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:repository-scan-fetch;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class RepositoryScanFetchPlanTest {
    @Autowired private EntityManager entityManager;
    @Autowired private RepositoryScanRepository scans;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void loadsFindingsAndProposalHistoryWithoutJoiningTwoBagCollections() {
        RepositoryScan scan = new RepositoryScan("org", "owner/repository", "main", "admin");
        RepositoryScanFinding finding = new RepositoryScanFinding("HIGH", "Validate input", "Description",
                "Impact", "Evidence", "src/Validator.java", "Reject empty input.");
        scan.claim("runner-1");
        scan.complete("runner-1", "a".repeat(40), "anthropic", "model", 10, 5, 100, true,
                List.of(finding));
        entityManager.persist(scan);
        entityManager.flush();

        entityManager.persist(new RepositoryIssueProposal("org", finding, "admin"));
        entityManager.persist(new RepositoryIssueProposal("org", finding, "admin"));
        entityManager.flush();
        entityManager.clear();

        RepositoryScan loaded = scans.findTop10ByOrganizationIdAndRepositoryOrderByCreatedAtDesc(
                "org", "owner/repository").getFirst();
        RepositoryScanFinding loadedFinding = loaded.getFindings().getFirst();

        assertEquals("Validate input", loadedFinding.getTitle());
        assertNotNull(loadedFinding.getProposal());
        assertEquals("PENDING", loadedFinding.getProposal().getStatus());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void serviceInitializesProposalHistoryBeforeReturningDetachedScanResults() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            RepositoryScan scan = new RepositoryScan("org", "owner/repository", "main", "admin");
            RepositoryScanFinding finding = new RepositoryScanFinding("HIGH", "Validate input", "Description",
                    "Impact", "Evidence", "src/Validator.java", "Reject empty input.");
            scan.claim("runner-1");
            scan.complete("runner-1", "b".repeat(40), "anthropic", "model", 10, 5, 100, true,
                    List.of(finding));
            entityManager.persist(scan);
            entityManager.flush();
            entityManager.persist(new RepositoryIssueProposal("org", finding, "admin"));
            entityManager.persist(new RepositoryIssueProposal("org", finding, "admin"));
        });

        RepositoryConnection connection = mock(RepositoryConnection.class);
        when(connection.isEnabled()).thenReturn(true);
        when(connection.belongsTo("org")).thenReturn(true);
        RepositoryConnectionRepository connections = mock(RepositoryConnectionRepository.class);
        when(connections.findByRepository("owner/repository")).thenReturn(Optional.of(connection));
        OperatorContext operators = mock(OperatorContext.class);
        when(operators.organizationId()).thenReturn("org");
        RepositoryScanService service = new RepositoryScanService(scans, connections, operators, null, null, null);

        List<RepositoryScan> detached = transaction.execute(status -> service.list("owner/repository"));

        assertNotNull(detached);
        assertNotNull(detached.getFirst().getFindings().getFirst().getProposal());
    }
}
