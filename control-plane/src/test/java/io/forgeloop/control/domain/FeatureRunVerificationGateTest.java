package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import java.util.List;

class FeatureRunVerificationGateTest {
  @Test
  void readiesRunOnlyAfterEveryRequiredGatePasses() {
    FeatureRun run = new FeatureRun("acme/widget", "main", "Add search", "spec", 10, "default", 1);
    run.addGate("unit");
    run.addGate("browser");

    run.recordGate("unit", true);
    assertEquals(RunState.RECEIVED, run.getState());

    run.recordGate("browser", true);
    assertEquals(RunState.READY_FOR_REVIEW, run.getState());
  }

  @Test
    void failedGateBlocksRunAndPassedGateCannotBeDowngraded() {
    FeatureRun run = new FeatureRun("acme/widget", "main", "Add search", "spec", 10, "default", 1);
    run.addGate("unit");

    run.recordGate("unit", false);
    assertEquals(RunState.BLOCKED, run.getState());

    FeatureRun passedRun = new FeatureRun("acme/widget", "main", "Add search", "spec", 10, "default", 1);
    passedRun.addGate("unit");
    passedRun.recordGate("unit", true);
    assertThrows(IllegalStateException.class, () -> passedRun.recordGate("unit", false));
    }

  @Test
  void cancellationHoldsOutstandingTasks() {
    FeatureRun run = new FeatureRun("acme/widget", "main", "Add search", "spec", 10, "default", 1);
    run.addTask("IMPLEMENTATION", "Implement", "provider");
    run.cancel();
    assertEquals(RunState.CANCELLED, run.getState());
    assertEquals(TaskState.HELD, run.getTasks().getFirst().getState());
  }

  @Test
  void policyGatesCreateIndependentRunnerTasksAndCoverCriteriaOnlyOnPass() {
    FeatureRun run = new FeatureRun("acme/widget", "main", "Add search", "spec", 10, "default", 1);
    run.addCriterion("Search returns authorized results");
    run.addPlannedTask("implementation", "IMPLEMENTATION", "Implement", "provider", List.of("src"), 2, 100);
    String image = "node@sha256:" + "a".repeat(64);
    run.addGate(new VerificationPolicySpec("unit", "CONTAINER", image, List.of("npm", "test"), "NONE", 300, true, "ALL"));
    run.addGate(new VerificationPolicySpec("advisory", "SECURITY", image, List.of("npm", "audit"), "EGRESS", 300, false, "ALL"));

    run.addPolicyVerificationTasks();

    assertEquals(2, run.getTasks().size());
    assertEquals("SKIPPED_BY_POLICY", run.getGates().get(1).getState());
    assertEquals("PENDING", run.getCriteria().getFirst().getCoverageState());
    run.recordGate("unit", true, false);
    assertEquals("COVERED", run.getCriteria().getFirst().getCoverageState());
    assertEquals(RunState.RECEIVED, run.getState());
    DeliveryTask verification = run.getTasks().getLast();
    verification.transition(TaskState.LEASED);
    verification.transition(TaskState.VERIFIED);
    run.evaluateReviewReadiness();
    assertEquals(RunState.READY_FOR_REVIEW, run.getState());
  }

  @Test
  void manualOverrideRemainsBlockedWithoutPassingEvidence() {
    FeatureRun run = new FeatureRun("acme/widget", "main", "Add search", "spec", 10, "default", 1);
    run.addGate("unit");
    run.recordGate("unit", false, true);

    run.overrideGate("unit");

    assertEquals("MANUAL_OVERRIDE", run.getGates().getFirst().getState());
    assertEquals(RunState.BLOCKED, run.getState());
  }

  @Test
  void allMappedRequiredGatesMustPassBeforeCriterionIsCovered() {
    FeatureRun run = new FeatureRun("acme/widget", "main", "Add search", "spec", 10, "default", 1);
    run.addCriterion("Search is authorized");
    String image = "node@sha256:" + "a".repeat(64);
    run.addGate(new VerificationPolicySpec("unit", "CONTAINER", image, List.of("npm", "test"), "NONE", 300, true, "ALL"));
    run.addGate(new VerificationPolicySpec("security", "SECURITY", image, List.of("npm", "audit"), "EGRESS", 300, true, "ALL"));

    run.recordGate("unit", true, false);
    assertEquals("PENDING", run.getCriteria().getFirst().getCoverageState());
    run.recordGate("security", true, false);
    assertEquals("COVERED", run.getCriteria().getFirst().getCoverageState());
  }

  @Test
  void approvalIsExplicitAndOnlyAvailableAfterVerification() {
    FeatureRun run = new FeatureRun("acme/widget", "main", "Add search", "spec", 10, "default", 1);
    assertThrows(IllegalStateException.class, () -> run.approve("operator@example.com"));
    run.addGate("unit");
    run.recordGate("unit", true);

    run.approve("operator@example.com");

    assertEquals(true, run.isApproved());
    assertEquals("operator@example.com", run.getApprovedBy());
    assertThrows(IllegalStateException.class, () -> run.approve("another@example.com"));
  }

  @Test
  void operatorRetryGrantsOneBoundedAttemptAndResumesBlockedRun() {
    FeatureRun run = new FeatureRun("acme/widget", "main", "Add search", "spec", 10, "default", 1);
    run.addTask("IMPLEMENTATION", "Implement", "provider");
    DeliveryTask task = run.getTasks().getFirst();
    for (int attempt = 0; attempt < 3; attempt++) {
      if (attempt > 0) task.transition(TaskState.LEASED);
      else task.transition(TaskState.LEASED);
      task.transition(TaskState.REPAIR_QUEUED);
    }
    assertEquals(TaskState.FAILED, task.getState());
    run.block();

    task.retryByOperator();
    run.resumeAfterRetry();

    assertEquals(TaskState.REPAIR_QUEUED, task.getState());
    assertEquals(3, task.getAttemptBudget());
    assertEquals(RunState.EXECUTING, run.getState());
  }
}
