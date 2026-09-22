package io.forgeloop.runner;

import java.util.List;

/** Provider-neutral task node proposed by the planner. */
public record PlannedTask(String key, String role, String title, String requiredCapability,
                          List<String> dependencies, List<String> ownedPaths,
                          int attemptBudget, long budgetMicros) {
    public PlannedTask {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        ownedPaths = ownedPaths == null ? List.of() : List.copyOf(ownedPaths);
    }
}
