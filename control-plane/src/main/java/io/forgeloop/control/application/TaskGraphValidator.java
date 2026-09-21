package io.forgeloop.control.application;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Validates planner output before any task is persisted or exposed to a runner. */
@Component
public final class TaskGraphValidator {
    private static final Set<String> ROLES = Set.of("IMPLEMENTATION", "BACKEND", "FRONTEND", "INDEPENDENT_TEST", "INTEGRATION", "REPAIR", "REVIEW");
    private static final Set<String> WRITING_ROLES = Set.of("IMPLEMENTATION", "BACKEND", "FRONTEND", "INDEPENDENT_TEST", "REPAIR");
    private static final int MAX_TASKS = 32;

    public void validate(TaskPlanSubmission plan, double runBudgetUsd) {
        if (plan.acceptanceCriteria().isEmpty() || plan.acceptanceCriteria().size() > 64) {
            throw new IllegalArgumentException("A plan must contain between 1 and 64 acceptance criteria");
        }
        if (plan.tasks().isEmpty() || plan.tasks().size() > MAX_TASKS) {
            throw new IllegalArgumentException("A plan must contain between 1 and 32 tasks");
        }
        requireDistinctNonBlank(plan.acceptanceCriteria(), "Acceptance criteria");
        Map<String, PlannedTaskSubmission> tasks = new HashMap<>();
        long allocatedMicros = 0;
        for (PlannedTaskSubmission task : plan.tasks()) {
            requireToken(task.key(), "Task key");
            if (tasks.put(task.key(), task) != null) throw new IllegalArgumentException("Task keys must be unique");
            if (!ROLES.contains(task.role())) throw new IllegalArgumentException("Unsupported task role: " + task.role());
            if (task.title() == null || task.title().isBlank() || task.title().length() > 300) throw new IllegalArgumentException("Task title is invalid");
            requireToken(task.requiredCapability(), "Required capability");
            if (task.attemptBudget() < 1 || task.attemptBudget() > 5) throw new IllegalArgumentException("Attempt budget must be between 1 and 5");
            if (task.budgetMicros() < 0) throw new IllegalArgumentException("Task budget cannot be negative");
            allocatedMicros = Math.addExact(allocatedMicros, task.budgetMicros());
            requireDistinctNonBlank(task.dependencies(), "Task dependencies");
            requireDistinctNonBlank(task.ownedPaths(), "Owned paths");
            if (WRITING_ROLES.contains(task.role()) && task.ownedPaths().isEmpty()) throw new IllegalArgumentException("Writing tasks require owned paths");
            task.ownedPaths().forEach(this::validatePathPrefix);
        }
        long runBudgetMicros = Math.round(runBudgetUsd * 1_000_000d);
        if (allocatedMicros > runBudgetMicros) throw new IllegalArgumentException("Task budgets exceed the run budget");
        plan.tasks().forEach(task -> task.dependencies().forEach(dependency -> {
            if (dependency.equals(task.key())) throw new IllegalArgumentException("A task cannot depend on itself");
            if (!tasks.containsKey(dependency)) throw new IllegalArgumentException("Unknown task dependency: " + dependency);
        }));
        detectCycles(tasks);
    }

    private static void detectCycles(Map<String, PlannedTaskSubmission> tasks) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String key : tasks.keySet()) visit(key, tasks, visiting, visited);
    }

    private static void visit(String key, Map<String, PlannedTaskSubmission> tasks, Set<String> visiting, Set<String> visited) {
        if (visited.contains(key)) return;
        if (!visiting.add(key)) throw new IllegalArgumentException("Task graph contains a cycle");
        for (String dependency : tasks.get(key).dependencies()) visit(dependency, tasks, visiting, visited);
        visiting.remove(key);
        visited.add(key);
    }

    private void validatePathPrefix(String raw) {
        if (raw == null || raw.isBlank() || raw.contains("\\") || raw.startsWith("/") || raw.contains("\u0000")) {
            throw new IllegalArgumentException("Owned path must be a repository-relative prefix");
        }
        Path normalized = Path.of(raw).normalize();
        if (normalized.startsWith("..") || normalized.toString().equals(".")) {
            throw new IllegalArgumentException("Owned path escapes the repository");
        }
    }

    private static void requireDistinctNonBlank(List<String> values, String label) {
        if (values.stream().anyMatch(value -> value == null || value.isBlank()) || new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException(label + " must be non-blank and unique");
        }
    }

    private static void requireToken(String value, String label) {
        if (value == null || !value.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException(label + " is invalid");
    }
}
