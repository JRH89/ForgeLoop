package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskLeaseTest {
    @Test
    void providerCompletionProducesChangeReadyRatherThanVerified() {
        FeatureRun run = new FeatureRun("owner/repository", "issue-1", "Feature", "criterion", 5, "GENERIC", 1);
        run.addTask("IMPLEMENTATION", "Implement feature", "git");
        DeliveryTask task = run.getTasks().getFirst();
        Runner runner = new Runner("organization", "runner", "1", List.of("git"), "credential-hash");
        TaskLease lease = new TaskLease(task, runner, "nonce-hash", Instant.now().plusSeconds(60));
        task.transition(TaskState.LEASED);

        lease.acknowledge();
        lease.completeChangeReady("a".repeat(40));

        assertEquals(TaskState.CHANGE_READY, task.getState());
        assertEquals("a".repeat(40), task.getChangeSha());
        assertEquals("a".repeat(40), lease.getResultSha());
        assertEquals(AttemptOutcome.CLEAN, lease.getOutcome());
        assertEquals("COMPLETED", lease.getOutcomeCategory());
    }

    @Test
    void inputReferencesAreWriteOnce() {
        FeatureRun run = new FeatureRun("owner/repository", "issue-1", "Feature", "criterion", 5, "GENERIC", 1);
        run.addTask("IMPLEMENTATION", "Implement feature", "git");
        DeliveryTask task = run.getTasks().getFirst();
        Runner runner = new Runner("organization", "runner", "1", List.of("git"), "credential-hash");
        TaskLease lease = new TaskLease(task, runner, "nonce-hash", Instant.now().plusSeconds(60));

        lease.captureInputRefs("{\"executionBaseRef\":\"main\"}");
        lease.captureInputRefs("{\"executionBaseRef\":\"main\"}");

        assertThrows(IllegalStateException.class,
                () -> lease.captureInputRefs("{\"executionBaseRef\":\"changed\"}"));
        assertEquals("{\"executionBaseRef\":\"main\"}", lease.getInputRefs());
    }

    @Test
    void failuresQueueOnlyTheOwningTaskAndExhaustionBlocksTheRun() {
        FeatureRun run = new FeatureRun("owner/repository", "issue-1", "Feature", "criterion", 5, "GENERIC", 1);
        DeliveryTask failing = run.addPlannedTask("backend", "BACKEND", "Backend", "git", List.of("src"), 1, 1_000_000);
        DeliveryTask independent = run.addPlannedTask("frontend", "FRONTEND", "Frontend", "git", List.of("web"), 1, 1_000_000);
        Runner runner = new Runner("organization", "runner", "1", List.of("git"), "credential-hash");

        failing.transition(TaskState.LEASED);
        TaskLease first = new TaskLease(failing, runner, "nonce-one", Instant.now().plusSeconds(60));
        first.acknowledge();
        first.complete(false);
        assertEquals(TaskState.REPAIR_QUEUED, failing.getState());
        assertEquals(AttemptOutcome.HARNESS_FAILURE, first.getOutcome());
        assertEquals("EXECUTION_FAILED", first.getOutcomeCategory());
        assertEquals(TaskState.PENDING, independent.getState());

        failing.transition(TaskState.LEASED);
        TaskLease second = new TaskLease(failing, runner, "nonce-two", Instant.now().plusSeconds(60));
        second.acknowledge();
        second.complete(false);
        assertEquals(TaskState.FAILED, failing.getState());
        assertEquals(RunState.BLOCKED, run.getState());
        assertEquals(AttemptOutcome.HARNESS_FAILURE, second.getOutcome());
    }

    @Test
    void retriesPreserveControlRolesAndOnlyConvertSourceWritersToRepair() {
        FeatureRun run = new FeatureRun("owner/repository", "issue-1", "Feature", "criterion", 5, "GENERIC", 1);
        DeliveryTask planner = run.addPlannedTask("planner", "PLANNER", "Plan", "provider", List.of(), 2, 1);
        DeliveryTask integration = run.addPlannedTask("integration", "INTEGRATION", "Integrate", "git", List.of(), 2, 1);
        DeliveryTask writer = run.addPlannedTask("writer", "BACKEND", "Write", "provider", List.of("src"), 2, 1);

        planner.transition(TaskState.LEASED);
        planner.transition(TaskState.REPAIR_QUEUED);
        integration.transition(TaskState.LEASED);
        integration.transition(TaskState.REPAIR_QUEUED);
        writer.transition(TaskState.LEASED);
        writer.transition(TaskState.REPAIR_QUEUED);

        assertEquals("PLANNER", planner.getExecutionRole());
        assertEquals("INTEGRATION", integration.getExecutionRole());
        assertEquals("REPAIR", writer.getExecutionRole());
    }

    @Test
    void integrationAdvancesOnlyDeclaredChangeReadyDependencies() {
        FeatureRun run = new FeatureRun("owner/repository", "issue-1", "Feature", "criterion", 5, "GENERIC", 1);
        DeliveryTask backend = run.addPlannedTask("backend", "BACKEND", "Backend", "git", List.of("src"), 2, 1);
        DeliveryTask frontend = run.addPlannedTask("frontend", "FRONTEND", "Frontend", "git", List.of("web"), 2, 1);
        DeliveryTask integration = run.addPlannedTask("integrate", "INTEGRATION", "Integrate", "git", List.of(), 2, 1);
        integration.dependsOn(backend);
        integration.dependsOn(frontend);
        backend.transition(TaskState.LEASED); backend.recordChangeSha("a".repeat(40)); backend.transition(TaskState.CHANGE_READY);
        frontend.transition(TaskState.LEASED); frontend.recordChangeSha("b".repeat(40)); frontend.transition(TaskState.CHANGE_READY);
        integration.transition(TaskState.LEASED);
        TaskLease lease = new TaskLease(integration, new Runner("org", "runner", "1", List.of("git"), "hash"),
                "nonce", Instant.now().plusSeconds(60));

        lease.acknowledge();
        lease.completeIntegration("c".repeat(40));

        assertEquals(TaskState.INTEGRATED, backend.getState());
        assertEquals(TaskState.INTEGRATED, frontend.getState());
        assertEquals(TaskState.INTEGRATED, integration.getState());
        assertEquals(AttemptOutcome.CLEAN, lease.getOutcome());
        assertEquals("COMPLETED", lease.getOutcomeCategory());
    }

    @Test
    void renewalIsCappedAtTheClaimBudgetAndHoldClosesTheLease() {
        FeatureRun run = new FeatureRun("owner/repository", "issue-2", "Feature", "criterion", 5, "GENERIC", 1);
        DeliveryTask task = run.addPlannedTask("backend", "BACKEND", "Backend", "provider", List.of("src"), 2, 1_000_000);
        task.transition(TaskState.LEASED);
        Instant claim = Instant.now();
        TaskLease lease = new TaskLease(task, new Runner("org", "runner", "1", List.of("provider"), "hash"),
                "nonce", claim.plusSeconds(600));
        lease.acknowledge();

        Instant renewAt = claim.plusSeconds(120);
        Instant cap = claim.plusSeconds(300);
        lease.renew(renewAt, cap);

        assertEquals(cap.toString(), lease.getExpiresAt());
        assertThrows(IllegalArgumentException.class, () -> lease.renew(renewAt, renewAt));
        lease.closeForHold();
        assertEquals(true, lease.isCompleted());
        assertEquals(AttemptOutcome.STOPPED, lease.getOutcome());
        assertEquals("WORKER_DECLINED", lease.getOutcomeCategory());
    }

    @Test
    void runnerBuildIsValidatedAndCannotBeChangedAfterAcknowledgement() {
        FeatureRun run = new FeatureRun("owner/repository", "issue-build", "Feature", "criterion", 5, "GENERIC", 1);
        run.addTask("IMPLEMENTATION", "Implement", "git");
        DeliveryTask task = run.getTasks().getFirst();
        TaskLease lease = new TaskLease(task, new Runner("organization", "runner", "1", List.of("git"), "hash"),
                "nonce", Instant.now().plusSeconds(60));

        assertThrows(IllegalArgumentException.class, () -> lease.acknowledge("branch", "not-a-digest"));
        lease.acknowledge("abcdef0123456", "a".repeat(64));

        assertEquals("abcdef0123456", lease.getRunnerRevision());
        assertEquals("a".repeat(64), lease.getRunnerJarSha256());
        assertThrows(IllegalStateException.class, () -> lease.acknowledge("abcdef0", "b".repeat(64)));
    }

    @Test
    void integrationPinsItsResultSha() {
        FeatureRun run = new FeatureRun("owner/repository", "issue-result", "Feature", "criterion", 5, "GENERIC", 1);
        DeliveryTask writer = run.addPlannedTask("writer", "BACKEND", "Write", "git", List.of("src"), 2, 1);
        DeliveryTask integration = run.addPlannedTask("integrate", "INTEGRATION", "Integrate", "git", List.of(), 2, 1);
        integration.dependsOn(writer);
        writer.transition(TaskState.LEASED);
        writer.recordChangeSha("a".repeat(40));
        writer.transition(TaskState.CHANGE_READY);
        integration.transition(TaskState.LEASED);
        TaskLease lease = new TaskLease(integration, new Runner("organization", "runner", "1", List.of("git"), "hash"),
                "nonce", Instant.now().plusSeconds(60));
        lease.acknowledge();

        lease.completeIntegration("b".repeat(40));

        assertEquals("b".repeat(40), lease.getResultSha());
        assertEquals(AttemptOutcome.CLEAN, lease.getOutcome());
        assertEquals("COMPLETED", lease.getOutcomeCategory());
    }

    @Test
    void recordsQualityPlannerAndPolicyHoldMeanings() {
        Runner runner = new Runner("org", "runner", "1", List.of("provider"), "hash");

        FeatureRun qualityRun = new FeatureRun("owner/quality", "issue-quality", "Quality", "criterion", 5, "GENERIC", 1);
        qualityRun.addTask("VERIFICATION", "Verify", "provider");
        DeliveryTask verification = qualityRun.getTasks().getFirst();
        verification.transition(TaskState.LEASED);
        TaskLease qualityLease = new TaskLease(verification, runner, "quality", Instant.now().plusSeconds(60));
        qualityLease.acknowledge();
        qualityLease.closeForRepairCycle();
        assertEquals(AttemptOutcome.FINDINGS, qualityLease.getOutcome());
        assertEquals("VERIFICATION_FAILED", qualityLease.getOutcomeCategory());

        FeatureRun planRun = new FeatureRun("owner/planner", "issue-plan", "Plan", "criterion", 5, "GENERIC", 1);
        planRun.addTask("PLANNER", "Plan", "provider");
        DeliveryTask planner = planRun.getTasks().getFirst();
        planner.transition(TaskState.LEASED);
        planner.transition(TaskState.VERIFIED);
        TaskLease plannerLease = new TaskLease(planner, runner, "planner", Instant.now().plusSeconds(60));
        plannerLease.acknowledge();
        plannerLease.completePlanning();
        assertEquals(AttemptOutcome.CLEAN, plannerLease.getOutcome());
        assertEquals("COMPLETED", plannerLease.getOutcomeCategory());

        FeatureRun holdRun = new FeatureRun("owner/hold", "issue-hold", "Hold", "criterion", 5, "GENERIC", 1);
        holdRun.addTask("INTEGRATION", "Integrate", "provider");
        DeliveryTask integration = holdRun.getTasks().getFirst();
        integration.transition(TaskState.LEASED);
        TaskLease policyLease = new TaskLease(integration, runner, "policy", Instant.now().plusSeconds(60));
        policyLease.acknowledge();
        policyLease.closeForPolicyHold("TEST_BOUNDARY_VIOLATION");
        assertEquals(AttemptOutcome.STOPPED, policyLease.getOutcome());
        assertEquals("TEST_BOUNDARY_VIOLATION", policyLease.getOutcomeCategory());
    }

    @Test
    void reservationIsReplaceableAndSettledWhenUsageIsRecordedOrLeaseCloses() {
        FeatureRun run = new FeatureRun("owner/repository", "issue-3", "Feature", "criterion", 5, "GENERIC", 1);
        DeliveryTask task = run.addPlannedTask("writer", "IMPLEMENTATION", "Write", "provider", List.of("src"), 1, 1_000);
        task.transition(TaskState.LEASED);
        TaskLease lease = new TaskLease(task, new Runner("org", "runner", "1", List.of("provider"), "hash"),
                "nonce", Instant.now().plusSeconds(60));
        lease.acknowledge();

        lease.reserve(400);
        lease.reserve(700);
        assertEquals(700, lease.getReservedMicros());
        lease.settleReservation();
        assertEquals(0, lease.getReservedMicros());

        lease.reserve(250);
        lease.closeForHold();
        assertEquals(0, lease.getReservedMicros());
    }
}
