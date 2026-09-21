package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.DeliveryTaskRepository;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.RunState;
import io.forgeloop.control.domain.Runner;
import io.forgeloop.control.domain.RunnerRepository;
import io.forgeloop.control.domain.ProviderAttemptRepository;
import io.forgeloop.control.domain.TaskLease;
import io.forgeloop.control.domain.TaskLeaseRepository;
import io.forgeloop.control.domain.TaskState;
import io.forgeloop.control.domain.VerificationEvidenceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TaskLeaseServiceTest {
    DeliveryTaskRepository tasks = mock(DeliveryTaskRepository.class);
    RunnerRepository runners = mock(RunnerRepository.class);
    TaskLeaseRepository leases = mock(TaskLeaseRepository.class);
    VerificationEvidenceRepository evidence = mock(VerificationEvidenceRepository.class);
    ProviderAttemptRepository providerAttempts = mock(ProviderAttemptRepository.class);
    TaskLeaseService service = new TaskLeaseService(tasks, runners, leases, evidence, providerAttempts);

    @Test
    void claimCreatesExpiringSingleOwnerLease() {
        FeatureRun run = new FeatureRun("a/b", "issue-1", "x", "- x", 1, "GENERIC", 1);
        run.addTask("IMPLEMENTATION", "x", "git");
        DeliveryTask task = run.getTasks().getFirst();
        Runner runner = new Runner("org", "node", "1", List.of("git"), "credential-hash");
        when(tasks.findById("task")).thenReturn(Optional.of(task));
        when(runners.findById("runner")).thenReturn(Optional.of(runner));
        when(leases.findByTask_Id("task")).thenReturn(Optional.empty());
        when(leases.save(any())).thenAnswer(call -> call.getArgument(0));

        LeaseGrant grant = service.claim("task", "runner");

        assertEquals(64, grant.nonce().length());
        assertEquals(TaskState.LEASED, task.getState());
    }

    @Test
    void namedGateEvidenceUpdatesTheOwningRun() {
        FeatureRun run = new FeatureRun("a/b", "issue-1", "x", "- x", 1, "GENERIC", 1);
        run.addTask("VERIFICATION", "unit", "docker");
        run.addGate("unit");
        DeliveryTask task = run.getTasks().getFirst();
        TaskLease lease = mock(TaskLease.class);
        Runner runner = mock(Runner.class);
        when(leases.findById("lease")).thenReturn(Optional.of(lease));
        when(lease.belongsTo("runner")).thenReturn(true);
        when(lease.matchesNonceHash(any())).thenReturn(true);
        when(lease.active()).thenReturn(true);
        when(lease.isAcknowledged()).thenReturn(true);
        when(lease.getTaskId()).thenReturn("task");
        when(tasks.findById("task")).thenReturn(Optional.of(task));
        when(runners.findById("runner")).thenReturn(Optional.of(runner));
        when(evidence.save(any())).thenAnswer(call -> call.getArgument(0));

        service.recordEvidence("lease", "runner", "nonce",
                new VerificationEvidenceSubmission("CONTAINER", "unit", "node:22-alpine", "npm test", 0, false, "passed"));

        assertEquals(RunState.READY_FOR_REVIEW, run.getState());
    }

    @Test
    void providerAttemptsRequireAndRemainBoundToAnAcknowledgedLease() {
        TaskLease lease = mock(TaskLease.class);
        DeliveryTask task = mock(DeliveryTask.class);
        Runner runner = mock(Runner.class);
        when(leases.findById("lease")).thenReturn(Optional.of(lease));
        when(lease.belongsTo("runner")).thenReturn(true);
        when(lease.matchesNonceHash(any())).thenReturn(true);
        when(lease.active()).thenReturn(true);
        when(lease.isAcknowledged()).thenReturn(true);
        when(lease.getTaskId()).thenReturn("task");
        when(task.getId()).thenReturn("task");
        when(tasks.findById("task")).thenReturn(Optional.of(task));
        when(runners.findById("runner")).thenReturn(Optional.of(runner));
        when(providerAttempts.findByTask_IdAndRequestIdDigest("task", "a".repeat(64))).thenReturn(Optional.empty());
        when(providerAttempts.save(any())).thenAnswer(call -> call.getArgument(0));

        var recorded = service.recordProviderAttempt("lease", "runner", "nonce",
                new ProviderAttemptSubmission("anthropic", "claude", "a".repeat(64), 10, 4, 2, "SUCCEEDED", 42, true, false, "COMPLETED"));

        assertEquals("anthropic", recorded.getProvider());
        assertEquals(2, recorded.getAttemptCount());
        assertEquals(42, recorded.getEstimatedCostMicros());
    }

    @Test
    void providerCompletionStopsAtChangeReadyBoundary() {
        TaskLease lease = mock(TaskLease.class);
        when(leases.findById("lease")).thenReturn(Optional.of(lease));
        when(lease.belongsTo("runner")).thenReturn(true);
        when(lease.matchesNonceHash(any())).thenReturn(true);

        service.completeProviderWork("lease", "runner", "nonce");

        verify(lease).completeChangeReady();
    }
}
