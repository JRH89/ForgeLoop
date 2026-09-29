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
    private static final Set<String> ROLES = Set.of("IMPLEMENTATION", "BACKEND", "FRONTEND", "INDEPENDENT_TEST", "INTEGRATION");
    private static final Set<String> WRITING_ROLES = Set.of("IMPLEMENTATION", "BACKEND", "FRONTEND", "INDEPENDENT_TEST", "REPAIR");
    private static final Map<String, String> ROLE_CAPABILITIES = Map.of(
            "IMPLEMENTATION", "provider", "BACKEND", "provider", "FRONTEND", "provider",
            "INDEPENDENT_TEST", "provider", "INTEGRATION", "git");
    private static final int MAX_TASKS = 32;

    public void validate(TaskPlanSubmission plan, double runBudgetUsd) {
        validate(plan, runBudgetUsd, false);
    }

    public void validate(TaskPlanSubmission plan, double runBudgetUsd, boolean testFirst) {
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
            if (!ROLE_CAPABILITIES.get(task.role()).equals(task.requiredCapability())) {
                throw new IllegalArgumentException("Task capability does not match its role: " + task.role());
            }
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
        List<PlannedTaskSubmission> integration=plan.tasks().stream().filter(task->"INTEGRATION".equals(task.role())).toList();
        Set<String> writing=plan.tasks().stream().filter(task->WRITING_ROLES.contains(task.role())).map(PlannedTaskSubmission::key).collect(java.util.stream.Collectors.toSet());
        if(integration.size()!=1||!integration.getFirst().dependencies().containsAll(writing)||!integration.getFirst().ownedPaths().isEmpty())throw new IllegalArgumentException("A plan requires exactly one pathless integration task depending on all writing tasks");
        plan.tasks().forEach(task -> task.dependencies().forEach(dependency -> {
            if (dependency.equals(task.key())) throw new IllegalArgumentException("A task cannot depend on itself");
            if (!tasks.containsKey(dependency)) throw new IllegalArgumentException("Unknown task dependency: " + dependency);
        }));
        for (PlannedTaskSubmission task : plan.tasks()) {
            if (WRITING_ROLES.contains(task.role()) && (task.dependencies().size() > 1
                    || task.dependencies().stream().anyMatch(key -> !WRITING_ROLES.contains(tasks.get(key).role())))) {
                throw new IllegalArgumentException("A writing task may depend on at most one other writing task");
            }
        }
        detectCycles(tasks);
        if (testFirst) validateTestFirstPlan(plan, tasks);
    }

    private static void validateTestFirstPlan(TaskPlanSubmission plan, Map<String, PlannedTaskSubmission> tasks) {
        List<PlannedTaskSubmission> tests = plan.tasks().stream().filter(task -> "INDEPENDENT_TEST".equals(task.role())).toList();
        if (tests.isEmpty()) throw new IllegalArgumentException("A test-first plan pairs every implementation writer with the test writer it depends on");
        for (PlannedTaskSubmission task : plan.tasks()) {
            if ("INDEPENDENT_TEST".equals(task.role())) {
                if (task.dependencies().size() > 1 || task.dependencies().stream()
                        .anyMatch(dependency -> !isImplementation(tasks.get(dependency).role()) || !isScaffold(dependency, plan.tasks()))) {
                    throw new IllegalArgumentException("A test-first plan pairs every implementation writer with the test writer it depends on");
                }
                boolean hasImplementation = plan.tasks().stream().anyMatch(candidate -> isImplementation(candidate.role())
                        && candidate.dependencies().contains(task.key()));
                if (!hasImplementation) throw new IllegalArgumentException("A test-first plan pairs every implementation writer with the test writer it depends on");
                continue;
            }
            if (!isImplementation(task.role())) continue;
            boolean implementation = task.dependencies().size() == 1
                    && "INDEPENDENT_TEST".equals(tasks.get(task.dependencies().getFirst()).role());
            boolean scaffold = task.dependencies().isEmpty() && isScaffold(task.key(), plan.tasks());
            if (!implementation && !scaffold) {
                throw new IllegalArgumentException("A test-first plan pairs every implementation writer with the test writer it depends on");
            }
        }
    }

    private static boolean isScaffold(String key, List<PlannedTaskSubmission> tasks) {
        // The server-owned integration node always depends on every writer; it is not a planner dependency.
        List<PlannedTaskSubmission> dependents = tasks.stream().filter(task -> task.dependencies().contains(key)
                && !"INTEGRATION".equals(task.role())).toList();
        return !dependents.isEmpty() && dependents.stream().allMatch(task -> "INDEPENDENT_TEST".equals(task.role()));
    }

    private static boolean isImplementation(String role) {
        return Set.of("IMPLEMENTATION", "BACKEND", "FRONTEND").contains(role);
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
