package io.forgeloop.control.application;

import java.util.List;

/** Exact, versioned planner result accepted by the control plane. */
public record TaskPlanSubmission(List<String> acceptanceCriteria, List<PlannedTaskSubmission> tasks) {
    public TaskPlanSubmission {
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}
