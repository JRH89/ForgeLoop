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
import io.forgeloop.control.domain.RepairPackageRepository;
import io.forgeloop.control.domain.TaskLease;
import io.forgeloop.control.domain.TaskLeaseRepository;
import io.forgeloop.control.domain.TaskState;
import io.forgeloop.control.domain.VerificationEvidenceRepository;
import io.forgeloop.control.domain.VerificationEvidence;
import io.forgeloop.control.domain.VerificationPolicySpec;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TaskLeaseServiceTest {
    DeliveryTaskRepository tasks = mock(DeliveryTaskRepository.class);
    RunnerRepository runners = mock(RunnerRepository.class);
    TaskLeaseRepository leases = mock(TaskLeaseRepository.class);
    VerificationEvidenceRepository evidence = mock(VerificationEvidenceRepository.class);
    ProviderAttemptRepository providerAttempts = mock(ProviderAttemptRepository.class);
    RepairPackageRepository repairPackages = mock(RepairPackageRepository.class);
    HumanEscalationService escalations = mock(HumanEscalationService.class);
    TaskLeaseService service = new TaskLeaseService(tasks, runners, leases, evidence, providerAttempts, repairPackages, escalations);

    @Test
    void claimCreatesExpiringSingleOwnerLease() {
        FeatureRun run = new FeatureRun("a/b", "issue-1", "x", "- x", 1, "GENERIC", 1);
        run.addTask("IMPLEMENTATION", "x", "git");
        run.beginPlanning();
        run.queuePlannedWork();
        DeliveryTask task = run.getTasks().getFirst();
        Runner runner = new Runner("org", "node", "1", List.of("git"), "credential-hash");
        when(tasks.findById("task")).thenReturn(Optional.of(task));
        when(tasks.findAllForUpdateByRunId(null)).thenReturn(List.of(task));
        when(runners.findById("runner")).thenReturn(Optional.of(runner));
        when(leases.findFirstByTask_IdOrderByExpiresAtDesc("task")).thenReturn(Optional.empty());
        when(leases.save(any())).thenAnswer(call -> call.getArgument(0));

        LeaseGrant grant = service.claim("task", "runner");

        assertEquals(64, grant.nonce().length());
        assertEquals(TaskState.LEASED, task.getState());
    }

    @Test
    void namedGateEvidenceUpdatesTheOwningRun() {
        FeatureRun run = new FeatureRun("a/b", "issue-1", "x", "- x", 1, "GENERIC", 1);
        String image = "node@sha256:" + "a".repeat(64);
        run.addGate(new VerificationPolicySpec("unit", "CONTAINER", image, List.of("npm", "test"), "NONE", 300, true, "ALL"));
        run.addPolicyVerificationTasks();
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

        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        String outputDigest = VerificationEvidence.digest("passed");
        String bundleDigest = VerificationEvidence.bundleDigest("CONTAINER", "unit", image, List.of("npm", "test"), 0, false, outputDigest, time, time, null);
        service.recordEvidence("lease", "runner", "nonce",
                new VerificationEvidenceSubmission("CONTAINER", "unit", image, List.of("npm", "test"), 0, false, "passed", time, time, null, outputDigest, bundleDigest));

        assertEquals(RunState.RECEIVED, run.getState());
    }

    @Test
    void providerAttemptsRequireAndRemainBoundToAnAcknowledgedLease() {
        TaskLease lease = mock(TaskLease.class);
        DeliveryTask task = mock(DeliveryTask.class);
        FeatureRun run = mock(FeatureRun.class);
        Runner runner = mock(Runner.class);
        when(leases.findById("lease")).thenReturn(Optional.of(lease));
        when(lease.belongsTo("runner")).thenReturn(true);
        when(lease.matchesNonceHash(any())).thenReturn(true);
        when(lease.active()).thenReturn(true);
        when(lease.isAcknowledged()).thenReturn(true);
        when(lease.getTaskId()).thenReturn("task");
        when(task.getId()).thenReturn("task");
        when(task.getRun()).thenReturn(run);
        when(run.getId()).thenReturn("run");
        when(run.getBudgetUsd()).thenReturn(10.0);
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
    void knownCostExhaustionBlocksTheOwningRun() {
        FeatureRun run = new FeatureRun("org", "a/b", "issue-2", "x", "spec", 1, "GENERIC", "main", 1);
        DeliveryTask task = run.addPlannedTask("implementation", "IMPLEMENTATION", "Implement", "provider", List.of("src"), 2, 50);
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
        when(providerAttempts.findByTask_IdAndRequestIdDigest(null, "b".repeat(64))).thenReturn(Optional.empty());
        when(providerAttempts.save(any())).thenAnswer(call -> call.getArgument(0));
        when(providerAttempts.sumKnownCostByTaskId(null)).thenReturn(50L);

        service.recordProviderAttempt("lease", "runner", "nonce",
                new ProviderAttemptSubmission("anthropic", "claude", "b".repeat(64), 1, 2, 1, "SUCCEEDED", 50, true, false, "COMPLETED"));

        assertEquals(RunState.BLOCKED, run.getState());
        verify(escalations).escalate(task, "BUDGET_EXHAUSTED", "Provider spend reached the configured task or run budget");
    }

    @Test
    void providerCompletionStopsAtChangeReadyBoundary() {
        TaskLease lease = mock(TaskLease.class);
        when(leases.findById("lease")).thenReturn(Optional.of(lease));
        when(lease.belongsTo("runner")).thenReturn(true);
        when(lease.matchesNonceHash(any())).thenReturn(true);

        service.completeProviderWork("lease", "runner", "nonce", "a".repeat(40));

        verify(lease).completeChangeReady("a".repeat(40));
    }
}
