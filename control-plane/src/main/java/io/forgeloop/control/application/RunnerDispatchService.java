package io.forgeloop.control.application;

import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.DeliveryTaskRepository;
import io.forgeloop.control.domain.TaskState;
import java.util.List;
import org.springframework.stereotype.Service;

/** Exposes only unleased work eligible for an authenticated runner to claim. */
@Service
public class RunnerDispatchService {
    private final DeliveryTaskRepository tasks;

    public RunnerDispatchService(DeliveryTaskRepository tasks) { this.tasks = tasks; }

    public List<DeliveryTask> available() {
        return tasks.findByStateIn(List.of(TaskState.PENDING, TaskState.REPAIR_QUEUED));
    }
}
