package io.forgeloop.control.application;

import java.util.List;

/** A planner-authored task whose identifiers are stable within one feature run. */
public record PlannedTaskSubmission(
        String key,
        String role,
        String title,
        String requiredCapability,
        List<String> dependencies,
        List<String> ownedPaths,
        int attemptBudget,
        long budgetMicros) {
    public PlannedTaskSubmission {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        ownedPaths = ownedPaths == null ? List.of() : List.copyOf(ownedPaths);
    }
}
