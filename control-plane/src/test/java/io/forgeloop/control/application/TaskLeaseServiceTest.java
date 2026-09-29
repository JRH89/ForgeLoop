package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

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
import io.forgeloop.control.domain.AgentLoopBudget;
import java.time.Instant;
import java.time.Duration;
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
        assertEquals("{\"executionBaseRef\":\"main\",\"verificationBaseRef\":\"main\",\"dependencyChangeShas\":[]}", grant.lease().getInputRefs());
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
        DeliveryTask integration = run.addPlannedTask("integrate", "INTEGRATION", "Integrate", "git", List.of(), 2, 0);
        integration.transition(TaskState.LEASED);
        integration.recordChangeSha("a".repeat(40));
        integration.transition(TaskState.CHANGE_READY);
        String image = "node@sha256:" + "a".repeat(64);
        run.addGate(new VerificationPolicySpec("unit", "CONTAINER", image, List.of("npm", "test"), "NONE", 300, true, "ALL"));
        run.addPolicyVerificationTasks();
        DeliveryTask task = run.getTasks().stream().filter(candidate -> "VERIFICATION".equals(candidate.getRole())).findFirst().orElseThrow();
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
        when(lease.getId()).thenReturn("lease");
        VerificationEvidence recorded = service.recordEvidence("lease", "runner", "nonce",
                new VerificationEvidenceSubmission("CONTAINER", "unit", image, List.of("npm", "test"), 0, false, "passed", time, time, null, outputDigest, bundleDigest,
                        "a".repeat(40), "sha256:" + "b".repeat(64), true));

        assertEquals(RunState.RECEIVED, run.getState());
        assertEquals("lease", recorded.getLeaseId());
        assertEquals("a".repeat(40), recorded.getTargetSha());
        assertEquals("sha256:" + "b".repeat(64), recorded.getImageId());
        assertEquals(true, recorded.getOutputTruncated());
    }

    @Test
    void namedGateEvidenceRejectsACommitOtherThanThePinnedVerificationBase() {
        FeatureRun run = new FeatureRun("a/b", "issue-1", "x", "- x", 1, "GENERIC", 1);
        DeliveryTask integration = run.addPlannedTask("integrate", "INTEGRATION", "Integrate", "git", List.of(), 2, 0);
        integration.transition(TaskState.LEASED);
        integration.recordChangeSha("a".repeat(40));
        integration.transition(TaskState.CHANGE_READY);
        String image = "node@sha256:" + "a".repeat(64);
        run.addGate(new VerificationPolicySpec("unit", "CONTAINER", image, List.of("npm", "test"), "NONE", 300, true, "ALL"));
        run.addPolicyVerificationTasks();
        DeliveryTask task = run.getTasks().stream().filter(candidate -> "VERIFICATION".equals(candidate.getRole())).findFirst().orElseThrow();
        TaskLease lease = mock(TaskLease.class);
        when(leases.findById("lease")).thenReturn(Optional.of(lease));
        when(lease.belongsTo("runner")).thenReturn(true);
        when(lease.matchesNonceHash(any())).thenReturn(true);
        when(lease.active()).thenReturn(true);
        when(lease.isAcknowledged()).thenReturn(true);
        when(lease.getTaskId()).thenReturn(task.getId());
        when(tasks.findById(task.getId())).thenReturn(Optional.of(task));
        when(runners.findById("runner")).thenReturn(Optional.of(mock(Runner.class)));

        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        String outputDigest = VerificationEvidence.digest("passed");
        String bundleDigest = VerificationEvidence.bundleDigest("CONTAINER", "unit", image, List.of("npm", "test"),
                0, false, outputDigest, time, time, null);
        VerificationEvidenceSubmission submission = new VerificationEvidenceSubmission("CONTAINER", "unit", image,
                List.of("npm", "test"), 0, false, "passed", time, time, null, outputDigest, bundleDigest,
                "b".repeat(40), null, false);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.recordEvidence("lease", "runner", "nonce", submission));

        assertEquals("Evidence target does not match the verification commit", failure.getMessage());
        verify(evidence, never()).save(any());
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
        when(lease.getId()).thenReturn("lease");
        when(task.getId()).thenReturn("task");
        when(task.getRun()).thenReturn(run);
        when(run.getId()).thenReturn("run");
        when(run.getBudgetUsd()).thenReturn(10.0);
        when(tasks.findById("task")).thenReturn(Optional.of(task));
        when(runners.findById("runner")).thenReturn(Optional.of(runner));
        when(providerAttempts.findByTask_IdAndRequestIdDigest("task", "a".repeat(64))).thenReturn(Optional.empty());
        when(providerAttempts.save(any())).thenAnswer(call -> call.getArgument(0));

        var recorded = service.recordProviderAttempt("lease", "runner", "nonce",
                new ProviderAttemptSubmission("anthropic", "claude", "a".repeat(64), 10, 4, 2, "SUCCEEDED", 42, true, false, "COMPLETED", "claude-actual-2026-09"));

        assertEquals("anthropic", recorded.getProvider());
        assertEquals(2, recorded.getAttemptCount());
        assertEquals(42, recorded.getEstimatedCostMicros());
        assertEquals("lease", recorded.getLeaseId());
        assertEquals("claude-actual-2026-09", recorded.getAnsweredModel());
        verify(lease).settleReservation();
    }

    @Test
    void spendReservationGrantsWithinTaskAndRunBudgetsAndReplacesPriorAmount() throws Exception {
        FeatureRun run = activeLoopRun();
        DeliveryTask task = run.getTasks().getFirst();
        TaskLease lease = realAcknowledgedLease(task);
        when(leases.findById("lease")).thenReturn(Optional.of(lease));
        when(tasks.findAllForUpdateByRunId(run.getId())).thenReturn(List.of(task));

        var first = service.reserveSpend("lease", "runner", "nonce", 20_000);
        var replacement = service.reserveSpend("lease", "runner", "nonce", 30_000);

        assertEquals(true, first.granted());
        assertEquals(20_000, first.reservedMicros());
        assertEquals(80_000, first.taskRemainingMicros());
        assertEquals(4_980_000, first.runRemainingMicros());
        assertEquals(30_000, replacement.reservedMicros());
        assertEquals(30_000, lease.getReservedMicros());
        verify(providerAttempts, org.mockito.Mockito.times(2)).sumKnownCostByTaskId(task.getId());
    }

    @Test
    void spendReservationRefusesTaskAndRunBudgetsAndClearsOldReservation() throws Exception {
        FeatureRun run = activeLoopRun();
        DeliveryTask task = run.getTasks().getFirst();
        TaskLease lease = realAcknowledgedLease(task);
        lease.reserve(10);
        when(leases.findById("lease")).thenReturn(Optional.of(lease));
        when(tasks.findAllForUpdateByRunId(run.getId())).thenReturn(List.of(task));
        when(providerAttempts.sumKnownCostByTaskId(task.getId())).thenReturn(10L);
        var taskRefusal = service.reserveSpend("lease", "runner", "nonce", 100_000);
        assertEquals(false, taskRefusal.granted());
        assertEquals(0, lease.getReservedMicros());

        when(providerAttempts.sumKnownCostByTaskId(task.getId())).thenReturn(0L);
        when(leases.sumActiveReservationsByRunExcludingLease(org.mockito.ArgumentMatchers.eq(run.getId()),
                org.mockito.ArgumentMatchers.eq("lease"), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(4_999_900L);
        var runRefusal = service.reserveSpend("lease", "runner", "nonce", 200);
        assertEquals(false, runRefusal.granted());
        assertEquals(0, runRefusal.reservedMicros());
        assertEquals(0, lease.getReservedMicros());
    }

    @Test
    void spendReservationLocksRunTasksBeforeReadingKnownSpendAndExcludesExpiredOrCompletedSiblingsByQuery() throws Exception {
        FeatureRun run = activeLoopRun();
        DeliveryTask task = run.getTasks().getFirst();
        TaskLease lease = realAcknowledgedLease(task);
        when(leases.findById("lease")).thenReturn(Optional.of(lease));
        when(tasks.findAllForUpdateByRunId(run.getId())).thenReturn(List.of(task));
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(tasks, providerAttempts, leases);

        service.reserveSpend("lease", "runner", "nonce", 1_000);

        order.verify(tasks).findAllForUpdateByRunId(run.getId());
        order.verify(providerAttempts).sumKnownCostByTaskId(task.getId());
        order.verify(providerAttempts).sumKnownCostByRunId(run.getId());
        order.verify(leases).sumActiveReservationsByRunExcludingLease(
                org.mockito.ArgumentMatchers.eq(run.getId()), org.mockito.ArgumentMatchers.eq("lease"),
                org.mockito.ArgumentMatchers.any(Instant.class));
    }

    @Test
    void spendReservationRequiresAnActiveAcknowledgedAgentLoopLeaseAndIntegralAmount() throws Exception {
        FeatureRun run = activeLoopRun();
        DeliveryTask task = run.getTasks().getFirst();
        TaskLease expired = new TaskLease(task, runnerForTests(),
                hashNonce("nonce"), Instant.now().minusSeconds(1));
        setEntityId(expired, "lease");
        when(leases.findById("lease")).thenReturn(Optional.of(expired));
        assertEquals("Lease is not active", assertThrows(IllegalArgumentException.class,
                () -> service.reserveSpend("lease", "runner", "nonce", 1)).getMessage());

        TaskLease lease = realAcknowledgedLease(task);
        when(leases.findById("lease")).thenReturn(Optional.of(lease));
        assertEquals("Spend reservation is invalid", assertThrows(IllegalArgumentException.class,
                () -> service.reserveSpend("lease", "runner", "nonce", 1.5)).getMessage());

        FeatureRun singleCall = activeRun();
        TaskLease singleCallLease = realAcknowledgedLease(singleCall.getTasks().getFirst());
        when(leases.findById("lease")).thenReturn(Optional.of(singleCallLease));
        assertEquals("Spend reservation requires an agent-loop task", assertThrows(IllegalArgumentException.class,
                () -> service.reserveSpend("lease", "runner", "nonce", 1)).getMessage());
    }

    @Test
    void zeroTaskBudgetMeansUnlimitedButStillRespectsTheRunBudget() throws Exception {
        FeatureRun run = new FeatureRun("org", "owner/repository", "issue-1", "x", "spec", 5,
                "GENERIC", "main", 1);
        DeliveryTask task = run.addPlannedTask("writer", "IMPLEMENTATION", "Write", "provider", List.of("src"), 2, 0);
        run.adoptAgentLoop(new AgentLoopBudget(20, 100_000, 600, 524_288));
        run.beginPlanning(); run.queuePlannedWork(); run.startExecution(); task.transition(TaskState.LEASED);
        TaskLease lease = realAcknowledgedLease(task);
        when(leases.findById("lease")).thenReturn(Optional.of(lease));
        when(tasks.findAllForUpdateByRunId(run.getId())).thenReturn(List.of(task));

        var reservation = service.reserveSpend("lease", "runner", "nonce", 500);

        assertEquals(true, reservation.granted());
        assertEquals(null, reservation.taskRemainingMicros());
        assertEquals(4_999_500, reservation.runRemainingMicros());
    }

    private TaskLease realAcknowledgedLease(DeliveryTask task) throws Exception {
        TaskLease lease = new TaskLease(task, runnerForTests(),
                hashNonce("nonce"), Instant.now().plusSeconds(600));
        setEntityId(lease, "lease");
        lease.acknowledge();
        return lease;
    }

    private static Runner runnerForTests() throws Exception {
        Runner runner = new Runner("org", "runner", "1", List.of("provider"), "hash");
        setEntityId(runner, "runner");
        return runner;
    }

    private static void setEntityId(Object entity, String id) throws Exception {
        var field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private static String hashNonce(String nonce) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(nonce.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
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

    @Test
    void renewalRequiresLoopPolicyAndUsesTheClaimTimeWallCap() {
        FeatureRun run = activeLoopRun();
        DeliveryTask task = run.getTasks().getFirst();
        TaskLease lease = authorizedLease(task);
        Instant claimedAt = Instant.now().minus(Duration.ofMinutes(2));
        when(lease.getClaimedAt()).thenReturn(claimedAt);

        service.renew("lease", "runner", "nonce");

        org.mockito.ArgumentCaptor<Instant> now = org.mockito.ArgumentCaptor.forClass(Instant.class);
        org.mockito.ArgumentCaptor<Instant> cap = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(lease).renew(now.capture(), cap.capture());
        assertEquals(claimedAt.plusSeconds(600 + 15 * 60), cap.getValue());
        assertEquals(true, now.getValue().isAfter(claimedAt));
    }

    @Test
    void renewalRefusesSingleCallTasksAndCancelledRuns() {
        FeatureRun unconfigured = activeRun();
        DeliveryTask task = unconfigured.getTasks().getFirst();
        TaskLease lease = authorizedLease(task);
        IllegalArgumentException noPolicy = assertThrows(IllegalArgumentException.class, () -> service.renew("lease", "runner", "nonce"));
        assertEquals("Lease renewal requires an agent-loop task", noPolicy.getMessage());

        FeatureRun cancelled = activeLoopRun();
        DeliveryTask cancelledTask = cancelled.getTasks().getFirst();
        cancelled.cancel();
        TaskLease cancelledLease = authorizedLease(cancelledTask);
        when(cancelledLease.getClaimedAt()).thenReturn(Instant.now().minusSeconds(30));
        IllegalArgumentException stopped = assertThrows(IllegalArgumentException.class, () -> service.renew("lease", "runner", "nonce"));
        assertEquals("Task is no longer running", stopped.getMessage());
    }

    @Test
    void holdStopsAndEscalatesButCancellationRaceOnlyClosesLease() {
        FeatureRun run = activeLoopRun();
        DeliveryTask task = run.getTasks().getFirst();
        TaskLease lease = authorizedLease(task);
        service.hold("lease", "runner", "nonce", "LOOP_BUDGET_EXHAUSTED", "Loop reached its token budget after 8 calls.");
        assertEquals(TaskState.HELD, task.getState());
        assertEquals(RunState.BLOCKED, run.getState());
        verify(lease).closeForHold();
        verify(escalations).escalate(task, "LOOP_BUDGET_EXHAUSTED", "Loop reached its token budget after 8 calls.");

        FeatureRun cancelled = activeLoopRun();
        DeliveryTask cancelledTask = cancelled.getTasks().getFirst();
        cancelled.cancel();
        TaskLease raced = authorizedLease(cancelledTask);
        service.hold("lease", "runner", "nonce", "WORKER_DECLINED", "Cancellation won the race.");
        verify(raced).closeForHold();
        verify(escalations, never()).escalate(eq(cancelledTask), any(), any());
        assertEquals(RunState.CANCELLED, cancelled.getState());
    }

    @Test
    void holdRejectsSecretsAndInvalidReasons() {
        FeatureRun run = activeLoopRun();
        TaskLease lease = authorizedLease(run.getTasks().getFirst());
        assertThrows(IllegalArgumentException.class, () -> service.hold("lease", "runner", "nonce", "OTHER", "A bounded summary."));
        assertThrows(IllegalArgumentException.class, () -> service.hold("lease", "runner", "nonce", "WORKER_DECLINED", "Authorization: Bearer sensitive-value"));
        verify(lease, never()).closeForHold();
    }

    @Test
    void holdAcceptsEveryEnforcementClassReason() {
        for (String reason : List.of("ENFORCEMENT_RULE_INPUT_MISSING", "ENFORCEMENT_PREREQUISITE_MISSING",
                "ENFORCEMENT_RULE_FAILED", "ENFORCEMENT_BOUNDARY_BREACHED")) {
            FeatureRun run = activeLoopRun();
            DeliveryTask task = run.getTasks().getFirst();
            authorizedLease(task);

            service.hold("lease", "runner", "nonce", reason, "Policy held this task for operator review.");

            assertEquals(TaskState.HELD, task.getState());
            assertEquals(RunState.BLOCKED, run.getState());
            verify(escalations).escalate(task, reason, "Policy held this task for operator review.");
        }
    }

    @Test
    void explicitFailureCategoryIsUsedOnlyForGeneralExecutionFailures() {
        FeatureRun run = activeRun();
        DeliveryTask task = run.getTasks().getFirst();
        TaskLease lease = authorizedLease(task);
        when(providerAttempts.findFirstByTask_IdOrderByRecordedAtDesc(task.getId())).thenReturn(Optional.empty());
        when(evidence.findFirstByTask_IdOrderByRecordedAtDesc(task.getId())).thenReturn(Optional.empty());
        when(repairPackages.save(any())).thenAnswer(call -> call.getArgument(0));

        service.complete("lease", "runner", "nonce", false, "LOOP_HARNESS_FAILURE");

        org.mockito.ArgumentCaptor<io.forgeloop.control.domain.RepairPackage> repair =
                org.mockito.ArgumentCaptor.forClass(io.forgeloop.control.domain.RepairPackage.class);
        verify(repairPackages).save(repair.capture());
        assertEquals("LOOP_HARNESS_FAILURE", repair.getValue().getFailureCategory());
        verify(lease).complete(false);
        assertThrows(IllegalArgumentException.class, () -> service.complete("lease", "runner", "nonce", false, "bad category"));
    }

    private FeatureRun activeRun() {
        FeatureRun run = new FeatureRun("org", "owner/repository", "issue-1", "x", "spec", 5, "GENERIC", "main", 1);
        run.addPlannedTask("writer", "IMPLEMENTATION", "Write", "provider", List.of("src"), 2, 100_000);
        run.beginPlanning(); run.queuePlannedWork(); run.startExecution();
        run.getTasks().getFirst().transition(TaskState.LEASED);
        return run;
    }

    private FeatureRun activeLoopRun() {
        FeatureRun run = activeRun();
        run.adoptAgentLoop(new AgentLoopBudget(20, 100_000, 600, 524_288));
        return run;
    }

    private TaskLease authorizedLease(DeliveryTask task) {
        TaskLease lease = mock(TaskLease.class);
        when(leases.findById("lease")).thenReturn(Optional.of(lease));
        when(lease.belongsTo("runner")).thenReturn(true);
        when(lease.matchesNonceHash(any())).thenReturn(true);
        when(lease.active()).thenReturn(true);
        when(lease.isAcknowledged()).thenReturn(true);
        when(lease.getTaskId()).thenReturn("task");
        when(tasks.findById("task")).thenReturn(Optional.of(task));
        return lease;
    }
}
