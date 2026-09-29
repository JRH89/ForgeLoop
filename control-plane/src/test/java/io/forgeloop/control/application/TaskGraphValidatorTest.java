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
                task("test", "INDEPENDENT_TEST", List.of(), List.of("harness"), 500_000),
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

    @Test
    void acceptsAWritingDependencyAndChainedWriterGraph() {
        TaskPlanSubmission plan = new TaskPlanSubmission(List.of("criterion"), List.of(
                task("tests", "INDEPENDENT_TEST", List.of(), List.of("tests"), 100),
                task("backend", "BACKEND", List.of("tests"), List.of("backend"), 100),
                task("implementation", "IMPLEMENTATION", List.of("backend"), List.of("src"), 100),
                task("integration", "INTEGRATION", List.of("implementation", "backend", "tests"), List.of(), 0)));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> validator.validate(plan, 1));
    }

    @Test
    void rejectsCyclesBetweenWritingTasks() {
        TaskPlanSubmission plan = new TaskPlanSubmission(List.of("criterion"), List.of(
                task("backend", "BACKEND", List.of("frontend"), List.of("backend"), 100),
                task("frontend", "FRONTEND", List.of("backend"), List.of("frontend"), 100),
                task("integration", "INTEGRATION", List.of("backend", "frontend"), List.of(), 0)));

        assertThrows(IllegalArgumentException.class, () -> validator.validate(plan, 1));
    }

    @Test
    void rejectsMultipleOrNonWritingDependenciesForAWriter() {
        TaskPlanSubmission multipleWriters = new TaskPlanSubmission(List.of("criterion"), List.of(
                task("tests", "INDEPENDENT_TEST", List.of(), List.of("tests"), 100),
                task("backend", "BACKEND", List.of(), List.of("backend"), 100),
                task("implementation", "IMPLEMENTATION", List.of("tests", "backend"), List.of("src"), 100),
                task("integration", "INTEGRATION", List.of("tests", "backend", "implementation"), List.of(), 0)));
        TaskPlanSubmission nonWritingDependency = new TaskPlanSubmission(List.of("criterion"), List.of(
                task("implementation", "IMPLEMENTATION", List.of(), List.of("src"), 100),
                task("integration", "INTEGRATION", List.of("implementation"), List.of(), 0),
                task("dependent", "BACKEND", List.of("integration"), List.of("backend"), 100)));

        assertThrows(IllegalArgumentException.class, () -> validator.validate(multipleWriters, 1));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(nonWritingDependency, 1));
    }

    private static PlannedTaskSubmission task(String key, String role, List<String> dependencies, List<String> paths, long budget) {
        return new PlannedTaskSubmission(key, role, key, "INTEGRATION".equals(role) ? "git" : "provider", dependencies, paths, 2, budget);
    }
}
