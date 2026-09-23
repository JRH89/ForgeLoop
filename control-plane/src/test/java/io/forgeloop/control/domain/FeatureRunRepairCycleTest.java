package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FeatureRunRepairCycleTest {
    @Test
    void failedVerificationCreatesCodeRepairAndReopensEveryQualityStage() {
        FeatureRun run = new FeatureRun("org", "acme/ticketly", "issue-1", "Feature", "Must pass", 10, "JVM_REACT", "main", 1);
        run.addCriterion("The feature works");
        run.addGate(new VerificationPolicySpec("unit", "CONTAINER", "image@sha256:" + "a".repeat(64), List.of("test"), "NONE", 60, true, "ALL"));
        DeliveryTask implementation = run.addPlannedTask("implementation", "IMPLEMENTATION", "Implement", "provider", List.of("src"), 2, 1_000);
        DeliveryTask integration = run.addPlannedTask("integration", "INTEGRATION", "Integrate", "git", List.of(), 2, 0);
        integration.dependsOn(implementation);
        run.addIndependentReviewTask();
        run.addPolicyVerificationTasks();
        DeliveryTask review = run.getTasks().stream().filter(task -> "REVIEW".equals(task.getRole())).findFirst().orElseThrow();
        DeliveryTask verification = run.getTasks().stream().filter(task -> "VERIFICATION".equals(task.getRole())).findFirst().orElseThrow();

        implementation.transition(TaskState.LEASED);
        implementation.recordChangeSha("b".repeat(40));
        implementation.transition(TaskState.CHANGE_READY);
        integration.transition(TaskState.LEASED);
        integration.integrateDependencies();
        integration.recordChangeSha("c".repeat(40));
        integration.transition(TaskState.INTEGRATED);
        review.transition(TaskState.LEASED);
        review.transition(TaskState.VERIFIED);
        verification.transition(TaskState.LEASED);

        RepairPackage repairPackage = run.scheduleQualityRepair(verification, "VERIFICATION_FAILED", "d".repeat(64));

        DeliveryTask repair = run.getTasks().stream().filter(task -> "REPAIR".equals(task.getRole())).findFirst().orElseThrow();
        assertNotNull(repairPackage);
        assertEquals(List.of("src"), repair.getOwnedPaths());
        assertEquals("c".repeat(40), repair.getExecutionBaseRef());
        assertTrue(repair.getExecutionSpecification().contains("VERIFICATION_FAILED"));
        assertTrue(integration.getDependencies().contains(repair));
        assertEquals(TaskState.PENDING, integration.getState());
        assertEquals(TaskState.PENDING, review.getState());
        assertEquals(TaskState.PENDING, verification.getState());
        assertEquals("PENDING", run.getGates().getFirst().getState());
        assertEquals(RunState.EXECUTING, run.getState());
    }

    @Test
    void verificationGatesRunSequentiallyToAvoidRepairingUnderActiveChecks() {
        FeatureRun run = new FeatureRun("acme/ticketly", "issue-1", "Feature", "Must pass", 10, "JVM_REACT", 1);
        run.addGate(new VerificationPolicySpec("first", "CONTAINER", "image@sha256:" + "a".repeat(64), List.of("one"), "NONE", 60, true, "ALL"));
        run.addGate(new VerificationPolicySpec("second", "CONTAINER", "image@sha256:" + "b".repeat(64), List.of("two"), "NONE", 60, true, "ALL"));
        run.addPolicyVerificationTasks();

        List<DeliveryTask> verification = run.getTasks().stream().filter(task -> "VERIFICATION".equals(task.getRole())).toList();

        assertEquals(List.of(verification.getFirst()), verification.getLast().getDependencies());
    }
}
