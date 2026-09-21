package io.forgeloop.runner;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

/** Strict planner output contract. The control plane independently validates graph semantics. */
public record PlannerPlan(List<String> acceptanceCriteria, List<PlannedTask> tasks) {
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public PlannerPlan {
        if (acceptanceCriteria == null || acceptanceCriteria.isEmpty() || tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("Planner plan is incomplete");
        }
        acceptanceCriteria = List.copyOf(acceptanceCriteria);
        tasks = List.copyOf(tasks);
    }

    public static PlannerPlan parse(String output) {
        try {
            JsonNode root = JSON.readTree(output);
            if (!root.isObject() || !hasExactly(root, "acceptanceCriteria", "tasks")
                    || !root.path("acceptanceCriteria").isArray() || !root.path("tasks").isArray()) {
                throw new IllegalArgumentException("Provider task plan does not match its schema");
            }
            List<String> criteria = textArray(root.path("acceptanceCriteria"), "acceptance criteria");
            List<PlannedTask> tasks = new ArrayList<>();
            for (JsonNode task : root.path("tasks")) {
                if (!task.isObject() || !hasExactly(task, "key", "role", "title", "requiredCapability", "dependencies", "ownedPaths", "attemptBudget", "budgetMicros")
                        || !task.path("key").isTextual() || !task.path("role").isTextual() || !task.path("title").isTextual()
                        || !task.path("requiredCapability").isTextual() || !task.path("dependencies").isArray()
                        || !task.path("ownedPaths").isArray() || !task.path("attemptBudget").canConvertToInt()
                        || !task.path("budgetMicros").canConvertToLong()) {
                    throw new IllegalArgumentException("Provider task node does not match its schema");
                }
                tasks.add(new PlannedTask(task.path("key").asText(), task.path("role").asText(), task.path("title").asText(),
                        task.path("requiredCapability").asText(), textArray(task.path("dependencies"), "dependencies"),
                        textArray(task.path("ownedPaths"), "owned paths"), task.path("attemptBudget").asInt(), task.path("budgetMicros").asLong()));
            }
            return new PlannerPlan(criteria, tasks);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Provider task plan is not valid JSON", exception);
        }
    }

    /** Performs runner-side semantic checks; the control plane repeats them before persistence. */
    public PlannerPlan validate(double budgetUsd) {
        if (acceptanceCriteria.size() > 64 || tasks.size() > 32 || new HashSet<>(acceptanceCriteria).size() != acceptanceCriteria.size()
                || acceptanceCriteria.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Planner criteria or task count is invalid");
        }
        Set<String> roles = Set.of("IMPLEMENTATION", "BACKEND", "FRONTEND", "INDEPENDENT_TEST", "INTEGRATION", "REPAIR", "REVIEW");
        Set<String> writingRoles = Set.of("IMPLEMENTATION", "BACKEND", "FRONTEND", "INDEPENDENT_TEST", "REPAIR");
        Map<String, PlannedTask> byKey = new HashMap<>();
        long allocated = 0;
        for (PlannedTask task : tasks) {
            if (task.key() == null || !task.key().matches("[A-Za-z0-9_.-]{1,80}") || byKey.put(task.key(), task) != null
                    || !roles.contains(task.role()) || task.title() == null || task.title().isBlank()
                    || task.requiredCapability() == null || !task.requiredCapability().matches("[A-Za-z0-9_.-]{1,80}")
                    || task.attemptBudget() < 1 || task.attemptBudget() > 5 || task.budgetMicros() < 0
                    || new HashSet<>(task.dependencies()).size() != task.dependencies().size()
                    || new HashSet<>(task.ownedPaths()).size() != task.ownedPaths().size()
                    || (writingRoles.contains(task.role()) && task.ownedPaths().isEmpty())
                    || task.ownedPaths().stream().anyMatch(PlannerPlan::unsafePath)) {
                throw new IllegalArgumentException("Planner task semantics are invalid");
            }
            allocated = Math.addExact(allocated, task.budgetMicros());
        }
        if (allocated > Math.round(budgetUsd * 1_000_000d)) throw new IllegalArgumentException("Planner task budgets exceed the run budget");
        tasks.forEach(task -> task.dependencies().forEach(dependency -> {
            if (dependency.equals(task.key()) || !byKey.containsKey(dependency)) throw new IllegalArgumentException("Planner dependency is invalid");
        }));
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        byKey.keySet().forEach(key -> visit(key, byKey, visiting, visited));
        return this;
    }

    private static void visit(String key, Map<String, PlannedTask> tasks, Set<String> visiting, Set<String> visited) {
        if (visited.contains(key)) return;
        if (!visiting.add(key)) throw new IllegalArgumentException("Planner task graph contains a cycle");
        tasks.get(key).dependencies().forEach(dependency -> visit(dependency, tasks, visiting, visited));
        visiting.remove(key);
        visited.add(key);
    }

    private static boolean unsafePath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/") || path.contains("\\") || path.contains("\u0000")) return true;
        java.nio.file.Path normalized = java.nio.file.Path.of(path).normalize();
        return normalized.startsWith("..") || normalized.toString().equals(".");
    }

    private static List<String> textArray(JsonNode node, String label) {
        List<String> values = new ArrayList<>();
        node.forEach(value -> {
            if (!value.isTextual()) throw new IllegalArgumentException(label + " must contain only strings");
            values.add(value.asText());
        });
        return List.copyOf(values);
    }

    private static boolean hasExactly(JsonNode node, String... names) {
        Set<String> expected = Set.of(names);
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        return actual.equals(expected);
    }
}
