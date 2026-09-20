package io.forgeloop.control.application;

import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.DeliveryTaskRepository;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.FeatureRunRepository;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.TaskState;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FeatureRunService {
  private final FeatureRunRepository runs; private final DeliveryTaskRepository tasks; private final RepositoryConnectionService connections; private final AuditLedgerService audit;
  public FeatureRunService(FeatureRunRepository runs, DeliveryTaskRepository tasks, RepositoryConnectionService connections, AuditLedgerService audit) { this.runs = runs; this.tasks = tasks; this.connections = connections; this.audit = audit; }
  @Transactional public FeatureRun submit(FeatureSubmission input) {
    if (runs.findByRepositoryAndSourceRef(input.repository(), input.sourceRef()).isPresent()) throw new IllegalStateException("A run already exists for this source reference");
    RepositoryConnection connection = connections.requireEnabled(input.repository());
    if (!connection.permitsBudget(input.budgetUsd())) throw new IllegalArgumentException("Requested budget exceeds repository policy");
    FeatureRun run = new FeatureRun(connection.getOrganizationId(), input.repository(), input.sourceRef(), input.title(), input.specification(), input.budgetUsd(), connection.getHarnessProfile(), connection.getPolicyRevision());
    run.addTask("PLANNER", "Derive acceptance criteria and task DAG", "provider");
    run.addTask("IMPLEMENTATION", "Implement scoped repository changes", "provider");
    run.addTask("INDEPENDENT_TEST", "Derive independent verification from acceptance criteria", "docker");
    for (String gate : connection.getRequiredGates()) run.addGate(gate);
    input.specification().lines().filter(line -> line.strip().startsWith("- ")).map(line -> line.strip().substring(2)).forEach(run::addCriterion);
    FeatureRun saved = runs.save(run); audit.record("FEATURE_RUN_SUBMITTED", "FEATURE_RUN", saved.getId() == null ? input.sourceRef() : saved.getId(), input.repository() + "|" + input.sourceRef()); return saved;
  }
  /** Idempotent GitHub issue intake protects against event retries and label changes. */
  @Transactional public FeatureRun submitIssue(FeatureSubmission input) { return runs.findByRepositoryAndSourceRef(input.repository(), input.sourceRef()).orElseGet(() -> submit(input)); }
  @Transactional public DeliveryTask transitionTask(String taskId, TaskState state) { DeliveryTask task = tasks.findById(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found")); connections.requireEnabled(task.getRun().getRepository()); task.transition(state); audit.record("TASK_TRANSITIONED", "TASK", taskId, state.name()); return task; }
  @Transactional public FeatureRun recordGate(String runId, String gate, boolean passed) { FeatureRun run = get(runId); run.recordGate(gate, passed); audit.record("VERIFICATION_GATE_RECORDED", "FEATURE_RUN", runId, gate + "|" + passed); return run; }
  @Transactional public FeatureRun cancel(String runId) { FeatureRun run = get(runId); run.cancel(); audit.record("FEATURE_RUN_CANCELLED", "FEATURE_RUN", runId, run.getState().name()); return run; }
  public FeatureRun get(String id) { FeatureRun run = runs.findById(id).orElseThrow(() -> new IllegalArgumentException("Feature run not found")); connections.requireEnabled(run.getRepository()); return run; }
  public List<FeatureRun> list() { return runs.findAll().stream().filter(run -> { try { connections.requireEnabled(run.getRepository()); return true; } catch (RuntimeException ignored) { return false; } }).toList(); }
}
