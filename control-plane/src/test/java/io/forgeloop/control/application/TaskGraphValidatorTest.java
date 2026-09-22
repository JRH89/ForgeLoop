package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class TaskGraphValidatorTest {
    private final TaskGraphValidator validator = new TaskGraphValidator();

    @Test
    void acceptsAcyclicBudgetedGraph() {
        TaskPlanSubmission plan = new TaskPlanSubmission(List.of("API returns the assigned user"), List.of(
                task("backend", "BACKEND", List.of(), List.of("control-plane/src"), 2_000_000),
                task("frontend", "FRONTEND", List.of(), List.of("frontend/src"), 2_000_000),
                task("test", "INDEPENDENT_TEST", List.of("backend", "frontend"), List.of("harness"), 500_000)));

        assertDoesNotThrow(() -> validator.validate(plan, 5));
    }

    @Test
    void rejectsCyclesTraversalAndBudgetOverflow() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(new TaskPlanSubmission(List.of("criterion"), List.of(
                task("a", "BACKEND", List.of("b"), List.of("src"), 1),
                task("b", "FRONTEND", List.of("a"), List.of("web"), 1))), 1));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(new TaskPlanSubmission(List.of("criterion"), List.of(
                task("a", "BACKEND", List.of(), List.of("../secret"), 1))), 1));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(new TaskPlanSubmission(List.of("criterion"), List.of(
                task("a", "BACKEND", List.of(), List.of("src"), 1_000_001))), 1));
    }

    private static PlannedTaskSubmission task(String key, String role, List<String> dependencies, List<String> paths, long budget) {
        return new PlannedTaskSubmission(key, role, key, "provider", dependencies, paths, 2, budget);
    }
}
