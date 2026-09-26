package io.forgeloop.control.application;

import io.forgeloop.control.domain.*;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates, redacts, and idempotently appends bounded runner progress events. */
@Service
public class RunnerEventService {
    private static final Set<String> LEVELS=Set.of("DEBUG","INFO","WARN","ERROR");
    private static final Set<String> TYPES=Set.of("LEASE_ACKNOWLEDGED","WORKSPACE_PREPARING","EXECUTION_STARTED","EXECUTION_PROGRESS","PROVIDER_STARTED","PROVIDER_COMPLETED","PROVIDER_FAILED","ARTIFACT_UPLOADED","SCREENSHOTS_UPLOADED","TASK_COMPLETED","TASK_FAILED");
    private final TaskLeaseService leases; private final TaskLeaseRepository leaseRepository; private final DeliveryTaskRepository tasks; private final RunnerEventRepository events;
    public RunnerEventService(TaskLeaseService leases,TaskLeaseRepository leaseRepository,DeliveryTaskRepository tasks,RunnerEventRepository events){this.leases=leases;this.leaseRepository=leaseRepository;this.tasks=tasks;this.events=events;}
    @Transactional public RunnerEvent append(String leaseId,String runnerId,String nonce,long sequence,String level,String type,String message,Instant occurredAt){
        if(sequence<1)throw new IllegalArgumentException("Event sequence must be positive");
        if(!LEVELS.contains(level)||!TYPES.contains(type))throw new IllegalArgumentException("Runner event classification is invalid");
        if(message==null||message.isBlank()||message.length()>2000)throw new IllegalArgumentException("Runner event message is invalid");
        if(occurredAt==null||occurredAt.isAfter(Instant.now().plusSeconds(60)))throw new IllegalArgumentException("Runner event timestamp is invalid");
        String taskId=leases.requireActiveTaskId(leaseId,runnerId,nonce);
        EvidenceSecretPolicy.requireRedacted(message);
        RunnerEvent existing=events.findByLeaseIdAndSequenceNumber(leaseId,sequence).orElse(null);
        if(existing!=null){if(!existing.matches(runnerId,level,type,message,occurredAt))throw new IllegalStateException("Event sequence was already used with different content");return existing;}
        TaskLease lease=leaseRepository.findById(leaseId).orElseThrow();
        DeliveryTask task=tasks.findById(taskId).orElseThrow();
        return events.save(new RunnerEvent(task.getRun().getOrganizationId(),task.getRun().getId(),taskId,runnerId,lease.getId(),sequence,level,type,message,occurredAt));
    }
}
