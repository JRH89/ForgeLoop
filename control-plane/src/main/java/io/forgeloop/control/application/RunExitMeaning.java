package io.forgeloop.control.application;

import io.forgeloop.control.domain.AttemptOutcome;
import io.forgeloop.control.domain.RunState;
import io.forgeloop.control.domain.TaskState;
import java.util.Comparator;
import java.util.List;

/** A derived explanation for how a run ended; it is not persisted. */
public record RunExitMeaning(AttemptOutcome outcome, String reason) {
    public record TaskExit(String taskId, TaskState state, AttemptOutcome outcome, String category,
                           String unresolvedReason) { }

    public static RunExitMeaning derive(RunState runState, List<TaskExit> tasks, String unresolvedRunReason) {
        if (runState == null) return new RunExitMeaning(null, null);
        if (List.of(RunState.READY_FOR_REVIEW, RunState.PR_OPEN, RunState.COMPLETE).contains(runState))
            return new RunExitMeaning(AttemptOutcome.CLEAN, "COMPLETED");
        if (runState == RunState.CANCELLED) return new RunExitMeaning(AttemptOutcome.STOPPED, "RUN_CANCELLED");
        if (runState != RunState.BLOCKED) return new RunExitMeaning(null, null);

        Comparator<TaskExit> byTaskId = Comparator.comparing(TaskExit::taskId,
                Comparator.nullsLast(Comparator.naturalOrder()));
        TaskExit held = tasks.stream().filter(task -> task.state() == TaskState.HELD).min(byTaskId).orElse(null);
        if (held != null) {
            String reason = firstNonBlank(held.category(), held.unresolvedReason(), "BLOCKED");
            return new RunExitMeaning(AttemptOutcome.STOPPED, reason);
        }

        TaskExit failed = tasks.stream().filter(task -> task.state() == TaskState.FAILED)
                .min(Comparator.comparingInt(RunExitMeaning::failurePriority).thenComparing(byTaskId)).orElse(null);
        if (failed != null) return new RunExitMeaning(failed.outcome(), failed.category());

        return new RunExitMeaning(AttemptOutcome.STOPPED, firstNonBlank(unresolvedRunReason, "BLOCKED"));
    }

    private static int failurePriority(TaskExit task) {
        if (task.outcome() == AttemptOutcome.HARNESS_FAILURE) return 0;
        if (task.outcome() == AttemptOutcome.FINDINGS) return 1;
        return 2;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }
}
