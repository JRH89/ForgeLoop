package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.forgeloop.control.domain.AttemptOutcome;
import io.forgeloop.control.domain.RunState;
import io.forgeloop.control.domain.TaskState;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunExitMeaningTest {
    @Test
    void completedDeliveryStatesHaveCleanMeaning() {
        for (RunState state : List.of(RunState.READY_FOR_REVIEW, RunState.PR_OPEN, RunState.COMPLETE))
            assertEquals(new RunExitMeaning(AttemptOutcome.CLEAN, "COMPLETED"), RunExitMeaning.derive(state, List.of(), null));
    }

    @Test
    void cancellationHasStoppedMeaning() {
        assertEquals(new RunExitMeaning(AttemptOutcome.STOPPED, "RUN_CANCELLED"),
                RunExitMeaning.derive(RunState.CANCELLED, List.of(), null));
    }

    @Test
    void activeStatesHaveNoExitMeaning() {
        for (RunState state : List.of(RunState.RECEIVED, RunState.VALIDATING, RunState.PLANNING,
                RunState.QUEUED, RunState.EXECUTING, RunState.INTEGRATING, RunState.VERIFYING, RunState.REVIEWING)) {
            RunExitMeaning meaning = RunExitMeaning.derive(state, List.of(), "IGNORED");
            assertNull(meaning.outcome());
            assertNull(meaning.reason());
        }
    }

    @Test
    void heldTaskPrecedesFailedTasksAndUsesItsOwnStopReason() {
        RunExitMeaning meaning = RunExitMeaning.derive(RunState.BLOCKED, List.of(
                task("failed", TaskState.FAILED, AttemptOutcome.HARNESS_FAILURE, "MCP_FAILED", null),
                task("held", TaskState.HELD, null, null, "WORKER_DECLINED")), "LATEST_ESCALATION");
        assertEquals(new RunExitMeaning(AttemptOutcome.STOPPED, "WORKER_DECLINED"), meaning);
    }

    @Test
    void harnessFailurePrecedesFindingsWhenSeveralTasksFailed() {
        RunExitMeaning meaning = RunExitMeaning.derive(RunState.BLOCKED, List.of(
                task("finding", TaskState.FAILED, AttemptOutcome.FINDINGS, "VERIFICATION_FAILED", null),
                task("harness", TaskState.FAILED, AttemptOutcome.HARNESS_FAILURE, "LEASE_EXPIRED", null)), null);
        assertEquals(new RunExitMeaning(AttemptOutcome.HARNESS_FAILURE, "LEASE_EXPIRED"), meaning);
    }

    @Test
    void legacyFailedLeaseDoesNotInventAnOutcome() {
        RunExitMeaning meaning = RunExitMeaning.derive(RunState.BLOCKED,
                List.of(task("legacy", TaskState.FAILED, null, null, null)), "IGNORED");
        assertNull(meaning.outcome());
        assertNull(meaning.reason());
    }

    @Test
    void blockedRunWithoutTaskOutcomeUsesLatestUnresolvedEscalation() {
        assertEquals(new RunExitMeaning(AttemptOutcome.STOPPED, "BUDGET_EXHAUSTED"),
                RunExitMeaning.derive(RunState.BLOCKED, List.of(), "BUDGET_EXHAUSTED"));
        assertEquals(new RunExitMeaning(AttemptOutcome.STOPPED, "BLOCKED"),
                RunExitMeaning.derive(RunState.BLOCKED, List.of(), null));
    }

    private static RunExitMeaning.TaskExit task(String id, TaskState state, AttemptOutcome outcome,
                                               String category, String escalation) {
        return new RunExitMeaning.TaskExit(id, state, outcome, category, escalation);
    }
}
