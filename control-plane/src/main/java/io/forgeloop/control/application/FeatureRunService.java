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
  private final FeatureRunRepository runs; private final DeliveryTaskRepository tasks; private final RepositoryConnectionService connections;
  public FeatureRunService(FeatureRunRepository runs, DeliveryTaskRepository tasks, RepositoryConnectionService connections) { this.runs = runs; this.tasks = tasks; this.connections = connections; }
  @Transactional public FeatureRun submit(FeatureSubmission input) {
    RepositoryConnection connection = connections.requireEnabled(input.repository());
    if (!connection.permitsBudget(input.budgetUsd())) throw new IllegalArgumentException("Requested budget exceeds repository policy");
    FeatureRun run = new FeatureRun(input.repository(), input.sourceRef(), input.title(), input.specification(), input.budgetUsd(), connection.getHarnessProfile(), connection.getPolicyRevision());
    run.addTask("PLANNER", "Derive acceptance criteria and task DAG"); run.addTask("IMPLEMENTATION", "Implement scoped repository changes"); run.addTask("INDEPENDENT_TEST", "Derive independent verification from acceptance criteria");
    for (String gate : connection.getRequiredGates()) run.addGate(gate);
    input.specification().lines().filter(line -> line.strip().startsWith("- ")).map(line -> line.strip().substring(2)).forEach(run::addCriterion);
    return runs.save(run);
  }
  @Transactional public DeliveryTask transitionTask(String taskId, TaskState state) { DeliveryTask task = tasks.findById(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found")); task.transition(state); return task; }
  public FeatureRun get(String id) { return runs.findById(id).orElseThrow(() -> new IllegalArgumentException("Feature run not found")); }
  public List<FeatureRun> list() { return runs.findAll(); }
}
