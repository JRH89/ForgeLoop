package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import io.forgeloop.control.domain.DeliveryTaskRepository;
import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.FeatureRunRepository;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.VerificationPolicySpec;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

class FeatureRunServiceTest {
  private final FeatureRunRepository runs = Mockito.mock(FeatureRunRepository.class); private final DeliveryTaskRepository tasks = Mockito.mock(DeliveryTaskRepository.class); private final RepositoryConnectionService connections = Mockito.mock(RepositoryConnectionService.class); private final AuditLedgerService audit = Mockito.mock(AuditLedgerService.class); private final PlatformConfigurationService platform=Mockito.mock(PlatformConfigurationService.class); private final FeatureRunService service = new FeatureRunService(runs, tasks, connections, audit, platform);
  @BeforeEach void configurePlatform(){when(platform.policy()).thenReturn(new io.forgeloop.control.domain.OrganizationPolicy("local-development",100,4,List.of("anthropic"),true));when(platform.requireHarness(Mockito.anyString())).thenReturn(Mockito.mock(io.forgeloop.control.domain.HarnessDefinition.class));}
  @Test void submissionUsesConnectedRepositoryPolicy() {
    String image = "node@sha256:" + "a".repeat(64);
    RepositoryConnection connection = new RepositoryConnection("local-development", "acme/support", 1, "main", "forgeloop", "JVM_REACT", List.of(
            new VerificationPolicySpec("compile", "CONTAINER", image, List.of("npm", "run", "build"), "NONE", 300, true, "ALL"),
            new VerificationPolicySpec("browser", "BROWSER", image, List.of("npm", "run", "e2e"), "NONE", 600, true, "ALL")), 25, true);
    when(connections.requireEnabled("acme/support")).thenReturn(connection); when(runs.save(any(FeatureRun.class))).thenAnswer(call -> call.getArgument(0));
    FeatureRun run = service.submit(new FeatureSubmission("acme/support", "issue-142", "Assignment", "- Admin can assign\n- Cross org is denied", 25));
    assertEquals("JVM_REACT", run.getHarnessProfile()); assertEquals(1, run.getPolicyRevision()); assertEquals(1, run.getTasks().size()); assertEquals(2, run.getGates().size()); assertEquals(0, run.getCriteria().size()); assertEquals(io.forgeloop.control.domain.RunState.PLANNING, run.getState()); verify(runs).save(run);
  }
  @Test void rejectsBudgetAboveRepositoryPolicy() {
    when(connections.requireEnabled("acme/support")).thenReturn(new RepositoryConnection("local-development", "acme/support", 1, "main", "forgeloop", "JVM_REACT", List.of("compile"), 10));
    assertThrows(IllegalArgumentException.class, () -> service.submit(new FeatureSubmission("acme/support", "issue-1", "Title", "- Criterion", 11)));
  }
  @Test void issueIntakeReusesExistingSourceRun() {
    FeatureRun existing = new FeatureRun("acme/support", "issue-142", "Assignment", "- criterion", 10, "JVM_REACT", 1);
    when(runs.findByRepositoryAndSourceRef("acme/support", "issue-142")).thenReturn(java.util.Optional.of(existing));
    assertEquals(existing, service.submitIssue(new FeatureSubmission("acme/support", "issue-142", "Assignment", "- criterion", 10)));
  }
  @Test void transitionRequiresAccessToTheTaskRunRepository() {
    FeatureRun run = new FeatureRun("local-development", "acme/support", "issue-1", "Title", "- criterion", 10, "JVM_REACT", 1);
    run.addTask("IMPLEMENTATION", "Implement", "provider");
    DeliveryTask task = run.getTasks().getFirst();
    when(tasks.findById("task-1")).thenReturn(java.util.Optional.of(task));
    doThrow(new IllegalStateException("Repository connection is unavailable")).when(connections).requireEnabled("acme/support");
    assertThrows(IllegalStateException.class, () -> service.transitionTask("task-1", io.forgeloop.control.domain.TaskState.RUNNING));
    verify(connections).requireEnabled("acme/support");
  }
}
