package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    TaskLeaseService service = new TaskLeaseService(tasks, runners, leases, evidence, providerAttempts, repairPackages, escalations,
            new ChainedWriterRunnerAffinity(leases));

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
    void claimRefusesAChainedWriterOnARunnerWithoutItsDependencyCommit() {
        FeatureRun run = new FeatureRun("org", "a/b", "issue-1", "x", "spec", 1, "GENERIC", "main", 1);
        DeliveryTask predecessor = run.addPlannedTask("tests", "INDEPENDENT_TEST", "Tests", "provider", List.of("tests"), 2, 100_000);
        DeliveryTask task = run.addPlannedTask("implementation", "IMPLEMENTATION", "Implement", "provider", List.of("src"), 2, 100_000);
        DeliveryTask redCheck = run.addPlannedTask("red-tests", "RED_CHECK", "Check tests first", "docker", List.of(), 2, 0);
        task.dependsOn(predecessor);
        task.dependsOn(redCheck);
        redCheck.dependsOn(predecessor);
        predecessor.transition(TaskState.LEASED);
        predecessor.recordChangeSha("a".repeat(40));
        predecessor.transition(TaskState.CHANGE_READY);
        redCheck.transition(TaskState.LEASED);
        redCheck.transition(TaskState.VERIFIED);
        TaskLease producerLease = mock(TaskLease.class);
        when(producerLease.getRunnerId()).thenReturn("runner-a");
        Runner otherRunner = mock(Runner.class);
        when(otherRunner.isEnabled()).thenReturn(true);
        when(otherRunner.hasCapability("provider")).thenReturn(true);
        when(otherRunner.getId()).thenReturn("runner-b");
        when(tasks.findById("task")).thenReturn(Optional.of(task));
        when(tasks.findAllForUpdateByRunId(run.getId())).thenReturn(List.of(task, predecessor, redCheck));
        when(runners.findById("runner-b")).thenReturn(Optional.of(otherRunner));
        when(leases.findFirstByTask_IdOrderByExpiresAtDesc(predecessor.getId())).thenReturn(Optional.of(producerLease));

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> service.claim("task", "runner-b"));

        assertEquals("Task must run on the runner that produced its dependency", failure.getMessage());
    }

    @Test
    void claimRefusesTestFirstRootWriterWithoutDockerCapability() {
        FeatureRun run = new FeatureRun("org", "a/b", "issue-1", "x", "spec", 1, "GENERIC", "main", 1);
        run.snapshotTestFirst("unit", List.of("**/*Test.java"));
        DeliveryTask task = run.addPlannedTask("tests", "INDEPENDENT_TEST", "Tests", "provider", List.of("src/test"), 2, 100_000);
        Runner runner = new Runner("org", "provider-only", "1", List.of("provider"), "credential-hash");
        when(tasks.findById("task")).thenReturn(Optional.of(task));
        when(tasks.findAllForUpdateByRunId(run.getId())).thenReturn(List.of(task));
        when(runners.findById("runner")).thenReturn(Optional.of(runner));

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> service.claim("task", "runner"));

        assertEquals("Test-first work must run on a runner that can run its checks", failure.getMessage());
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
    void genericLeaseCompletionCannotApproveRedOrGreenChecks() {
        for (String role : List.of("RED_CHECK", "GREEN_CHECK")) {
            DeliveryTask task = mock(DeliveryTask.class);
            when(task.getRole()).thenReturn(role);
            when(task.getId()).thenReturn("task");
            acknowledgedLease();
            when(tasks.findById("task")).thenReturn(Optional.of(task));

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> service.complete("lease", "runner", "nonce", true));

            assertEquals("Test checks require checksummed test evidence", failure.getMessage());
        }
    }

    @Test
    void genericVerificationEvidenceCannotBeRecordedForTestCheckTask() {
        DeliveryTask task = mock(DeliveryTask.class);
        when(task.getRole()).thenReturn("RED_CHECK");
        acknowledgedLease();
        when(tasks.findById("task")).thenReturn(Optional.of(task));
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        VerificationEvidenceSubmission submission = new VerificationEvidenceSubmission("CONTAINER", "unit",
                "node@sha256:" + "a".repeat(64), List.of("npm", "test"), 0, false, "passed", time, time,
                null, "a".repeat(64), "b".repeat(64));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.recordEvidence("lease", "runner", "nonce", submission));

        assertEquals("Verification evidence requires a verification task", failure.getMessage());
    }

    private TaskLease acknowledgedLease() {
        TaskLease lease = mock(TaskLease.class);
        when(leases.findById("lease")).thenReturn(Optional.of(lease));
        when(lease.belongsTo("runner")).thenReturn(true);
        when(lease.matchesNonceHash(any())).thenReturn(true);
        when(lease.active()).thenReturn(true);
        when(lease.isAcknowledged()).thenReturn(true);
        when(lease.getTaskId()).thenReturn("task");
        return lease;
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

    @Test
    void testBoundaryViolationHoldsIntegrationBlocksRunAndClosesLease() throws Exception {
        FeatureRun run = new FeatureRun("org", "acme/project", "issue-1", "Build feature", "spec", 1,
                "GENERIC", "main", 1);
        DeliveryTask integration = run.addPlannedTask("integration", "INTEGRATION", "Integrate", "git", List.of(), 1, 0);
        run.beginPlanning();
        run.queuePlannedWork();
        run.startExecution();
        Runner runner = mock(Runner.class);
        when(runner.getId()).thenReturn("runner");
        String nonceHash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest("nonce".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        TaskLease lease = new TaskLease(integration, runner, nonceHash, Instant.now().plusSeconds(600));
        lease.acknowledge();
        when(leases.findById("lease")).thenReturn(Optional.of(lease));

        service.holdIntegrationForTestBoundaryViolation("lease", "runner", "nonce", "test file changed");

        assertEquals(TaskState.HELD, integration.getState());
        assertEquals(RunState.BLOCKED, run.getState());
        org.junit.jupiter.api.Assertions.assertTrue(lease.isCompleted());
        verify(escalations).escalate(integration, "TEST_BOUNDARY_VIOLATION", "test file changed");
    }
}
