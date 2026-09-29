package io.forgeloop.control.application;

import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.Runner;
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
        if ("RED_CHECK".equals(task.getRole())) {
            return task.getWritingDependency().map(dependency -> sameRunnerAsDependency(dependency, runnerId)).orElse(false);
        }
        if ("GREEN_CHECK".equals(task.getRole())) {
            return task.getDependencies().stream().filter(dependency -> "INTEGRATION".equals(dependency.getRole()))
                    .findFirst().map(dependency -> sameRunnerAsDependency(dependency, runnerId)).orElse(false);
        }
        if (!task.isWritingTask() || task.getDependencies().isEmpty()) return true;
        if (task.getDependencies().stream().filter(DeliveryTask::isWritingTask).count() != 1) return false;
        var dependency = task.getWritingDependency();
        if (dependency.isEmpty()) return false;
        return sameRunnerAsDependency(dependency.get(), runnerId);
    }

    private boolean sameRunnerAsDependency(DeliveryTask predecessor, String runnerId) {
        if (predecessor.getChangeSha() == null || runnerId == null || runnerId.isBlank()) return false;
        return leases.findFirstByTask_IdOrderByExpiresAtDesc(predecessor.getId())
                .map(lease -> Objects.equals(lease.getRunnerId(), runnerId))
                .orElse(false);
    }

    /** Root test writers and scaffolds run the RED check locally and therefore need Docker. */
    public boolean requiresDocker(DeliveryTask task) {
        return task.getRun().isTestFirst() && ((task.isWritingTask()
                && !"REPAIR".equals(task.getRole()) && task.getWritingDependency().isEmpty())
                || "RED_CHECK".equals(task.getRole()) || "GREEN_CHECK".equals(task.getRole()));
    }

    public boolean permits(DeliveryTask task, Runner runner) {
        if (runner == null || (requiresDocker(task) && !runner.hasCapability("docker"))) return false;
        return permits(task, runner.getId());
    }
}
