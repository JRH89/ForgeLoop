package io.forgeloop.control.application;

import io.forgeloop.control.domain.TaskLease;
import io.forgeloop.control.domain.TaskLeaseRepository;
import io.forgeloop.control.domain.RepairPackage;
import io.forgeloop.control.domain.RepairPackageRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Returns timed-out work to the bounded repair queue so a dead runner cannot strand a run. */
@Service
public class LeaseRecoveryService {
    private final TaskLeaseRepository leases;
    private final RepairPackageRepository repairPackages;
    private final HumanEscalationService escalations;
    public LeaseRecoveryService(TaskLeaseRepository leases, RepairPackageRepository repairPackages, HumanEscalationService escalations) {
        this.leases = leases; this.repairPackages = repairPackages; this.escalations=escalations;
    }
    @Scheduled(fixedDelayString = "${forgeloop.runner.lease-recovery-delay-ms:30000}")
    @Transactional public void recoverExpiredLeases() {
        List<TaskLease> expired = leases.findByCompletedAtIsNullAndExpiresAtBefore(Instant.now());
        for (TaskLease lease : expired) {
            if (lease.recover()) {
                repairPackages.save(new RepairPackage(lease.getTask(), "LEASE_EXPIRED", null));
                if (lease.getTask().getState() == io.forgeloop.control.domain.TaskState.FAILED)
                    escalations.escalate(lease.getTask(), "ATTEMPT_BUDGET_EXHAUSTED", "Runner lease expired after all autonomous attempts");
            }
        }
        leases.deleteAll(expired);
    }
}
