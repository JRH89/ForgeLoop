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
                task("test", "INDEPENDENT_TEST", List.of("backend", "frontend"), List.of("harness"), 500_000),
                task("integration", "INTEGRATION", List.of("backend","frontend","test"), List.of(), 0)));

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

    @Test
    void rejectsPlannerSelectedCapabilitiesThatDoNotMatchServerRoles() {
        PlannedTaskSubmission invalid = new PlannedTaskSubmission(
                "frontend", "FRONTEND", "frontend", "frontend-development",
                List.of(), List.of("frontend/src"), 2, 1);
        PlannedTaskSubmission integration = new PlannedTaskSubmission(
                "integration", "INTEGRATION", "integration", "git",
                List.of("frontend"), List.of(), 2, 0);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                new TaskPlanSubmission(List.of("criterion"), List.of(invalid, integration)), 1));
    }

    private static PlannedTaskSubmission task(String key, String role, List<String> dependencies, List<String> paths, long budget) {
        return new PlannedTaskSubmission(key, role, key, "INTEGRATION".equals(role) ? "git" : "provider", dependencies, paths, 2, budget);
    }
}
