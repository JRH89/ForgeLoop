package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.forgeloop.control.domain.DeliveryTaskRepository;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.FeatureRunRepository;
import io.forgeloop.control.domain.RepositoryConnection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FeatureRunServiceTest {
  private final FeatureRunRepository runs = Mockito.mock(FeatureRunRepository.class); private final DeliveryTaskRepository tasks = Mockito.mock(DeliveryTaskRepository.class); private final RepositoryConnectionService connections = Mockito.mock(RepositoryConnectionService.class); private final AuditLedgerService audit = Mockito.mock(AuditLedgerService.class); private final FeatureRunService service = new FeatureRunService(runs, tasks, connections, audit);
  @Test void submissionUsesConnectedRepositoryPolicy() {
    RepositoryConnection connection = new RepositoryConnection("acme/support", 1, "main", "forgeloop", "JVM_REACT", List.of("compile", "browser"), 25);
    when(connections.requireEnabled("acme/support")).thenReturn(connection); when(runs.save(any(FeatureRun.class))).thenAnswer(call -> call.getArgument(0));
    FeatureRun run = service.submit(new FeatureSubmission("acme/support", "issue-142", "Assignment", "- Admin can assign\n- Cross org is denied", 25));
    assertEquals("JVM_REACT", run.getHarnessProfile()); assertEquals(1, run.getPolicyRevision()); assertEquals(3, run.getTasks().size()); assertEquals(2, run.getGates().size()); assertEquals(2, run.getCriteria().size()); verify(runs).save(run);
  }
  @Test void rejectsBudgetAboveRepositoryPolicy() {
    when(connections.requireEnabled("acme/support")).thenReturn(new RepositoryConnection("acme/support", 1, "main", "forgeloop", "JVM_REACT", List.of("compile"), 10));
    assertThrows(IllegalArgumentException.class, () -> service.submit(new FeatureSubmission("acme/support", "issue-1", "Title", "- Criterion", 11)));
  }
}
