package io.forgeloop.control.application;

import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.TaskLeaseRepository;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Keeps a chained writer on the runner-local repository that owns its predecessor commit. */
@Component
public final class ChainedWriterRunnerAffinity {
    private final TaskLeaseRepository leases;

    public ChainedWriterRunnerAffinity(TaskLeaseRepository leases) {
        this.leases = leases;
    }

    public boolean permits(DeliveryTask task, String runnerId) {
        if (!task.isWritingTask() || task.getDependencies().isEmpty()) return true;
        if (task.getDependencies().stream().filter(DeliveryTask::isWritingTask).count() != 1) return false;
        var dependency = task.getWritingDependency();
        if (dependency.isEmpty()) return false;
        DeliveryTask predecessor = dependency.get();
        if (predecessor.getChangeSha() == null || runnerId == null || runnerId.isBlank()) return false;
        return leases.findFirstByTask_IdOrderByExpiresAtDesc(predecessor.getId())
                .map(lease -> Objects.equals(lease.getRunnerId(), runnerId))
                .orElse(false);
    }
}
