package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

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
}
