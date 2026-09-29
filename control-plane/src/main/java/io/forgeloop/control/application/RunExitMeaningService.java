package io.forgeloop.control.application;

import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.DeliveryTaskRepository;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.HumanEscalation;
import io.forgeloop.control.domain.HumanEscalationRepository;
import io.forgeloop.control.domain.TaskLease;
import io.forgeloop.control.domain.TaskLeaseRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads lease and escalation history in batches to derive stable meanings for GraphQL run lists. */
@Service
public class RunExitMeaningService {
    private final TaskLeaseRepository leases;
    private final HumanEscalationRepository escalations;
    private final DeliveryTaskRepository tasks;

    public RunExitMeaningService(TaskLeaseRepository leases, HumanEscalationRepository escalations,
                                 DeliveryTaskRepository tasks) {
        this.leases = leases;
        this.escalations = escalations;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public Map<String, RunExitMeaning> derive(Collection<FeatureRun> runs) {
        if (runs == null || runs.isEmpty()) return Map.of();
        List<String> runIds = runs.stream().map(FeatureRun::getId).filter(java.util.Objects::nonNull).distinct().toList();
        if (runIds.isEmpty()) return Map.of();

        Map<String, TaskLease> latestLeaseByTask = new HashMap<>();
        for (TaskLease lease : leases.findClosedByRunIds(runIds))
            latestLeaseByTask.putIfAbsent(lease.getTaskId(), lease);
        Map<String, String> latestRunReason = new HashMap<>();
        Map<String, String> latestTaskReason = new HashMap<>();
        for (HumanEscalation escalation : escalations.findUnresolvedByRunIds(runIds)) {
            latestRunReason.putIfAbsent(escalation.getRunId(), escalation.getReason());
            if (escalation.getTaskId() != null)
                latestTaskReason.putIfAbsent(escalation.getRunId() + "\u0000" + escalation.getTaskId(), escalation.getReason());
        }

        Map<String, List<DeliveryTask>> tasksByRun = new HashMap<>();
        for (DeliveryTask task : tasks.findByRun_IdIn(runIds))
            tasksByRun.computeIfAbsent(task.getRun().getId(), ignored -> new java.util.ArrayList<>()).add(task);

        Map<String, RunExitMeaning> result = new LinkedHashMap<>();
        for (FeatureRun run : runs) {
            List<RunExitMeaning.TaskExit> taskExits = tasksByRun.getOrDefault(run.getId(), List.of()).stream().map(task -> {
                TaskLease lease = latestLeaseByTask.get(task.getId());
                String taskReason = latestTaskReason.get(run.getId() + "\u0000" + task.getId());
                return new RunExitMeaning.TaskExit(task.getId(), task.getState(), lease == null ? null : lease.getOutcome(),
                        lease == null ? null : lease.getOutcomeCategory(), taskReason);
            }).toList();
            result.put(run.getId(), RunExitMeaning.derive(run.getState(), taskExits, latestRunReason.get(run.getId())));
        }
        return result;
    }
}
