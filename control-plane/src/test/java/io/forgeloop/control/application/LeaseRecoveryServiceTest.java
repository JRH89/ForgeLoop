package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.Runner;
import io.forgeloop.control.domain.TaskLease;
import io.forgeloop.control.domain.TaskLeaseRepository;
import io.forgeloop.control.domain.TaskState;
import io.forgeloop.control.domain.RepairPackageRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LeaseRecoveryServiceTest {
    private final TaskLeaseRepository leases = Mockito.mock(TaskLeaseRepository.class);
    private final RepairPackageRepository repairs = Mockito.mock(RepairPackageRepository.class);
    private final HumanEscalationService escalations = Mockito.mock(HumanEscalationService.class);
    private final LeaseRecoveryService recovery = new LeaseRecoveryService(leases, repairs, escalations);

    @Test
    void returnsExpiredLeaseTaskToRepairQueueAndRemovesLease() {
        FeatureRun run = new FeatureRun("org/repository", "main", "title", "spec", 1, "GENERIC", 1);
        run.addTask("IMPLEMENTATION", "Implement", "provider");
        TaskLease expired = new TaskLease(run.getTasks().getFirst(), new Runner("org", "runner", "1", List.of("provider"), "hash"),
                "nonce-hash", Instant.now().minusSeconds(1));
        run.getTasks().getFirst().transition(TaskState.LEASED);
        when(leases.findByCompletedAtIsNullAndExpiresAtBefore(Mockito.any())).thenReturn(List.of(expired));

        recovery.recoverExpiredLeases();

        assertEquals(TaskState.REPAIR_QUEUED, run.getTasks().getFirst().getState());
        verify(repairs).save(Mockito.any());
        verify(leases).deleteAll(List.of(expired));
    }

    @Test
    void closesExpiredLeaseWithoutRepairingACancelledRun() {
        FeatureRun run = new FeatureRun("org/repository", "main", "title", "spec", 1, "GENERIC", 1);
        run.addTask("IMPLEMENTATION", "Implement", "provider");
        DeliveryTaskAccess.lease(run);
        TaskLease expired = new TaskLease(run.getTasks().getFirst(), new Runner("org", "runner", "1", List.of("provider"), "hash"),
                "cancelled-nonce", Instant.now().minusSeconds(1));
        run.cancel();
        when(leases.findByCompletedAtIsNullAndExpiresAtBefore(Mockito.any())).thenReturn(List.of(expired));

        recovery.recoverExpiredLeases();

        verify(repairs, never()).save(Mockito.any());
        verify(leases).deleteAll(List.of(expired));
    }

    /** Keeps aggregate setup explicit without exposing mutable task collections in production code. */
    private static final class DeliveryTaskAccess {
        static void lease(FeatureRun run) { run.getTasks().getFirst().transition(TaskState.LEASED); }
    }
}
