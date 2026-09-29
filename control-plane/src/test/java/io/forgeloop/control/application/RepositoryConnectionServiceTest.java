package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import io.forgeloop.control.security.OperatorContext;
import io.forgeloop.control.domain.OrganizationMembershipRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RepositoryConnectionServiceTest {
  private final RepositoryConnectionRepository repository = Mockito.mock(RepositoryConnectionRepository.class); private final RepositoryConnectionService service = new RepositoryConnectionService(repository, new OperatorContext("development", "local-development", Mockito.mock(OrganizationMembershipRepository.class)), Mockito.mock(AuditLedgerService.class));
  @Test void registersARepositoryWithPolicy() { when(repository.findByRepository("acme/support")).thenReturn(Optional.empty()); when(repository.save(any(RepositoryConnection.class))).thenAnswer(call -> call.getArgument(0)); RepositoryConnection connection = service.register(new RepositoryRegistration("acme/support", 12, "main", "forgeloop", "JVM_REACT", List.of("compile", "browser"), 20)); assertEquals("forgeloop", connection.getIssueLabel()); assertEquals(List.of("compile", "browser"), connection.getRequiredGates()); }
  @Test void rejectsDuplicateRepository() { when(repository.findByRepository("acme/support")).thenReturn(Optional.of(new RepositoryConnection("local-development", "acme/support", 12, "main", "forgeloop", "JVM_REACT", List.of("compile"), 20))); assertThrows(IllegalStateException.class, () -> service.register(new RepositoryRegistration("acme/support", 12, "main", "forgeloop", "JVM_REACT", List.of("compile"), 20))); }
  @Test void rejectsConnectionOwnedByAnotherOrganization() { when(repository.findByRepository("other/support")).thenReturn(Optional.of(new RepositoryConnection("other", "other/support", 12, "main", "forgeloop", "JVM_REACT", List.of("compile"), 20))); assertThrows(IllegalStateException.class, () -> service.requireEnabled("other/support")); }
  @Test void listsOnlyTheCurrentOrganizationsPolicyFetchedConnections() { RepositoryConnection connection = new RepositoryConnection("local-development", "acme/support", 12, "main", "forgeloop", "JVM_REACT", List.of("compile"), 20); when(repository.findByOrganizationId("local-development")).thenReturn(List.of(connection)); assertEquals(List.of(connection), service.list()); }
  @Test void configuresTestFirstForAnEnabledRepository() {
    RepositoryConnection connection = new RepositoryConnection("local-development", "acme/support", 12, "main", "forgeloop", "JVM_REACT", List.of("unit"), 20);
    String image = "node@sha256:" + "a".repeat(64);
    connection.replaceVerificationPolicies(List.of(new io.forgeloop.control.domain.VerificationPolicySpec("unit", "CONTAINER", image,
            List.of("npm", "test", "--junitxml=/forgeloop/test-report/unit.xml"), "NONE", 300, true, "ALL", "JUNIT_XML")));
    when(repository.findByRepository("acme/support")).thenReturn(Optional.of(connection));
    when(repository.save(any(RepositoryConnection.class))).thenAnswer(call -> call.getArgument(0));

    RepositoryConnection configured = service.configureTestFirst("acme/support", "unit", List.of("**/*.test.ts"));

    assertEquals("unit", configured.getTestFirstGate());
    assertEquals(List.of("**/*.test.ts"), configured.getTestPathGlobs());
  }
}
