package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import io.forgeloop.control.domain.AgentLoopBudget;
import io.forgeloop.control.security.OperatorContext;
import io.forgeloop.control.domain.OrganizationMembershipRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RepositoryConnectionServiceTest {
  private final RepositoryConnectionRepository repository = Mockito.mock(RepositoryConnectionRepository.class);
  private final AuditLedgerService audit = Mockito.mock(AuditLedgerService.class);
  private final RepositoryConnectionService service = new RepositoryConnectionService(repository,
          new OperatorContext("development", "local-development", Mockito.mock(OrganizationMembershipRepository.class)), audit);
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
  @Test void configuresAndDisablesAnAuditedRepositoryLoopBudget() {
    RepositoryConnection connection = new RepositoryConnection("local-development", "acme/support", 12, "main", "forgeloop", "JVM_REACT", List.of("compile"), 20);
    when(repository.findByRepository("acme/support")).thenReturn(Optional.of(connection));
    when(repository.save(any(RepositoryConnection.class))).thenAnswer(call -> call.getArgument(0));
    RepositoryConnection configured = service.configureAgentLoop("acme/support", new AgentLoopBudget(25, 250_000, 600, 524_288));
    assertEquals(2, configured.getPolicyRevision());
    assertEquals(25, configured.getAgentLoopBudget().getMaxToolCalls());
    RepositoryConnection disabled = service.configureAgentLoop("acme/support", null);
    assertEquals(3, disabled.getPolicyRevision());
    assertEquals(null, disabled.getAgentLoopBudget());
  }
  @Test void runRecordIsOffByDefaultAndConfigurationIsAuditedAndRevisioned() {
    RepositoryConnection connection = new RepositoryConnection("local-development", "acme/support", 12, "main", "forgeloop", "JVM_REACT", List.of("compile"), 20);
    assertFalse(connection.isRunRecordEnabled());
    when(repository.findByRepository("acme/support")).thenReturn(Optional.of(connection));
    when(repository.save(any(RepositoryConnection.class))).thenAnswer(call -> call.getArgument(0));
    int initialRevision = connection.getPolicyRevision();

    RepositoryConnection enabled = service.configureRunRecord("acme/support", true);

    assertTrue(enabled.isRunRecordEnabled());
    assertEquals(initialRevision + 1, enabled.getPolicyRevision());
    verify(repository).save(connection);
    verify(audit).record("REPOSITORY_RUN_RECORD_UPDATED", "REPOSITORY_CONNECTION", connection.getId(),
            "enabled=true|revision=" + enabled.getPolicyRevision());
  }
  @Test void runRecordMutationRequiresAdministratorBeforeLoadingRepository() {
    OperatorContext viewer = Mockito.mock(OperatorContext.class);
    doThrow(new org.springframework.security.access.AccessDeniedException("administrator required"))
            .when(viewer).requireAdministrator();
    RepositoryConnectionService restricted = new RepositoryConnectionService(repository, viewer, audit);

    assertThrows(org.springframework.security.access.AccessDeniedException.class,
            () -> restricted.configureRunRecord("acme/support", true));
    verify(repository, never()).findByRepository("acme/support");
  }
  @Test void configuresAuditedEnforcementAndCanRestoreRepositoryDefaults() {
    RepositoryConnection connection = connectionWithPolicies();
    when(repository.findByRepository("acme/support")).thenReturn(Optional.of(connection));
    when(repository.save(any(RepositoryConnection.class))).thenAnswer(call -> call.getArgument(0));
    int initialRevision = connection.getPolicyRevision();

    RepositoryConnection configured = service.configureEnforcement("acme/support", List.of("src/private/**"), true, "verify");

    assertEquals(initialRevision + 1, configured.getPolicyRevision());
    assertEquals(List.of("src/private/**"), configured.getEnforcement().protectedPaths());
    assertTrue(configured.getEnforcement().allowWorkflowChanges());
    assertEquals("verify", configured.getEnforcement().finishGate());
    org.mockito.ArgumentCaptor<String> material = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(audit).record(eq("REPOSITORY_ENFORCEMENT_UPDATED"), eq("REPOSITORY_CONNECTION"), isNull(), material.capture());
    assertTrue(material.getValue().contains("protectedPaths=src/private/**"));
    assertTrue(material.getValue().contains("allowWorkflowChanges=true"));
    assertTrue(material.getValue().contains("finishGate=verify"));

    RepositoryConnection reset = service.configureEnforcement("acme/support", List.of(), false, null);
    assertEquals(List.of(), reset.getEnforcement().protectedPaths());
    assertFalse(reset.getEnforcement().allowWorkflowChanges());
    assertEquals(null, reset.getEnforcement().finishGate());
    assertEquals(initialRevision + 2, reset.getPolicyRevision());
  }
  @Test void rejectsInvalidProtectedGlobsAndUnknownFinishGateWithoutChangingPolicy() {
    RepositoryConnection connection = connectionWithPolicies();
    int initialRevision = connection.getPolicyRevision();
    assertThrows(IllegalArgumentException.class,
            () -> connection.configureEnforcement(List.of("../private/**"), false, null));
    assertThrows(IllegalArgumentException.class,
            () -> connection.configureEnforcement(java.util.stream.IntStream.range(0, 65)
                    .mapToObj(index -> "private" + index + "/**").toList(), false, null));
    assertThrows(IllegalArgumentException.class,
            () -> connection.configureEnforcement(List.of(), false, "missing"));
    assertEquals(initialRevision, connection.getPolicyRevision());
  }
  @Test void requiresAdministratorAndEnabledRepositoryForEnforcementChanges() {
    io.forgeloop.control.security.OperatorContext viewer = Mockito.mock(io.forgeloop.control.security.OperatorContext.class);
    doThrow(new org.springframework.security.access.AccessDeniedException("administrator required"))
            .when(viewer).requireAdministrator();
    RepositoryConnectionService restricted = new RepositoryConnectionService(repository, viewer, audit);
    assertThrows(org.springframework.security.access.AccessDeniedException.class,
            () -> restricted.configureEnforcement("acme/support", List.of(), false, null));
    verify(repository, never()).findByRepository("acme/support");

    RepositoryConnection disabled = connectionWithPolicies();
    disabled.disable();
    when(repository.findByRepository("acme/support")).thenReturn(Optional.of(disabled));
    assertThrows(IllegalStateException.class,
            () -> service.configureEnforcement("acme/support", List.of(), false, null));
  }
  @Test void cannotConfigureAnotherOrganizationsRepositoryEnforcement() {
    RepositoryConnection foreign = new RepositoryConnection("another-organization", "acme/support", 12, "main", "forgeloop", "JVM_REACT", List.of("verify"), 20);
    when(repository.findByRepository("acme/support")).thenReturn(Optional.of(foreign));

    assertThrows(IllegalStateException.class,
            () -> service.configureEnforcement("acme/support", List.of("src/private/**"), false, null));
    verify(repository, never()).save(any(RepositoryConnection.class));
  }

  private static RepositoryConnection connectionWithPolicies() {
    RepositoryConnection connection = new RepositoryConnection("local-development", "acme/support", 12, "main", "forgeloop", "JVM_REACT", List.of("verify"), 20);
    String image = "node@sha256:" + "a".repeat(64);
    connection.replaceVerificationPolicies(List.of(new io.forgeloop.control.domain.VerificationPolicySpec("verify", "CONTAINER", image,
            List.of("npm", "test"), "NONE", 300, true, "ALL")));
    return connection;
  }
}
