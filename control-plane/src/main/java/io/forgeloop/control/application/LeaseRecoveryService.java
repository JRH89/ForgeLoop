package io.forgeloop.control.application;

import io.forgeloop.control.domain.TaskLease;
import io.forgeloop.control.domain.TaskLeaseRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Returns timed-out work to the bounded repair queue so a dead runner cannot strand a run. */
@Service
public class LeaseRecoveryService {
    private final TaskLeaseRepository leases;
    public LeaseRecoveryService(TaskLeaseRepository leases) { this.leases = leases; }
    @Scheduled(fixedDelayString = "${forgeloop.runner.lease-recovery-delay-ms:30000}")
    @Transactional public void recoverExpiredLeases() {
        List<TaskLease> expired = leases.findByCompletedAtIsNullAndExpiresAtBefore(Instant.now());
        for (TaskLease lease : expired) lease.recover();
        leases.deleteAll(expired);
    }
}
