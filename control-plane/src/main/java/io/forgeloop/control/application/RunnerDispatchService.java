package io.forgeloop.control.application;

import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.DeliveryTaskRepository;
import io.forgeloop.control.domain.TaskState;
import io.forgeloop.control.domain.Runner;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exposes only unleased work eligible for an authenticated runner to claim. */
@Service
public class RunnerDispatchService {
    private final DeliveryTaskRepository tasks;

    public RunnerDispatchService(DeliveryTaskRepository tasks) { this.tasks = tasks; }

    @Transactional(readOnly = true)
    public List<DeliveryTask> available(Runner runner) {
        List<DeliveryTask> active = tasks.findByStateIn(List.of(TaskState.LEASED, TaskState.PREPARING, TaskState.RUNNING));
        List<DeliveryTask> available = tasks.findByStateIn(List.of(TaskState.PENDING, TaskState.REPAIR_QUEUED)).stream()
                .filter(task -> runner.hasCapability(task.getRequiredCapability()))
                .filter(DeliveryTask::dependenciesSatisfied)
                .filter(DeliveryTask::hasBudgetRemaining)
                .filter(task -> task.getRun().hasBudgetRemaining())
                .filter(candidate -> active.stream()
                        .filter(task -> Objects.equals(task.getRun().getId(), candidate.getRun().getId()))
                        .noneMatch(candidate::pathConflictsWith))
                .toList();
        // GraphQL serializes after this transaction closes. Materialize every lazy aggregate
        // used by the runner contract here so dispatch never depends on Open Session in View.
        available.forEach(task -> {
            task.getAcceptanceCriteria();
            task.getDependencyChangeShas();
            task.getExecutionSpecification();
            task.getVerificationGateName();
        });
        return available;
    }
}
