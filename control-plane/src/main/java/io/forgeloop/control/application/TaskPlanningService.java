package io.forgeloop.control.application;

import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.DeliveryTaskRepository;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.FeatureRunRepository;
import io.forgeloop.control.domain.TaskState;
import io.forgeloop.control.domain.TaskLeaseRepository;
import jakarta.transaction.Transactional;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Atomically materializes a validated planner graph; partial plans never become schedulable. */
@Service
public class TaskPlanningService {
    private final DeliveryTaskRepository tasks;
    private final FeatureRunRepository runs;
    private final TaskGraphValidator validator;
    private final AuditLedgerService audit;
    private final TaskLeaseRepository leases;

    public TaskPlanningService(DeliveryTaskRepository tasks, FeatureRunRepository runs,
                               TaskGraphValidator validator, AuditLedgerService audit, TaskLeaseRepository leases) {
        this.tasks = tasks; this.runs = runs; this.validator = validator; this.audit = audit; this.leases = leases;
    }

    @Transactional
    public FeatureRun submit(String plannerTaskId, TaskPlanSubmission plan) {
        DeliveryTask planner = tasks.findById(plannerTaskId).orElseThrow(() -> new IllegalArgumentException("Planner task not found"));
        if (!"PLANNER".equals(planner.getRole())) throw new IllegalArgumentException("Only a planner task can submit a task graph");
        if (planner.getState() != TaskState.RUNNING && planner.getState() != TaskState.LEASED) {
            throw new IllegalStateException("Planner task is not active");
        }
        FeatureRun run = planner.getRun();
        if (run.getTasks().size() != 1) throw new IllegalStateException("A task graph has already been materialized");
        validator.validate(plan, run.getBudgetUsd());

        plan.acceptanceCriteria().forEach(run::addCriterion);
        Map<String, DeliveryTask> materialized = new HashMap<>();
        plan.tasks().forEach(input -> materialized.put(input.key(), run.addPlannedTask(input.key(), input.role(), input.title(),
                input.requiredCapability(), input.ownedPaths(), input.attemptBudget(), input.budgetMicros())));
        plan.tasks().forEach(input -> input.dependencies().forEach(key -> materialized.get(input.key()).dependsOn(materialized.get(key))));
        planner.transition(TaskState.VERIFIED);
        leases.findByTask_Id(plannerTaskId).orElseThrow(() -> new IllegalStateException("Planner lease not found")).completePlanning();
        run.queuePlannedWork();
        FeatureRun saved = runs.save(run);
        audit.record("TASK_GRAPH_MATERIALIZED", "FEATURE_RUN", run.getId(), "tasks=" + plan.tasks().size() + "|criteria=" + plan.acceptanceCriteria().size());
        saved.getCriteria();
        saved.getTasks().forEach(task -> { task.getDependencies(); task.getProviderAttempts(); task.getRepairPackages(); });
        return saved;
    }
}
