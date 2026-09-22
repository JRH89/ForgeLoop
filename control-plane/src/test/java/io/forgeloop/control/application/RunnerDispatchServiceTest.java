package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import io.forgeloop.control.domain.DeliveryTaskRepository;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.Runner;
import io.forgeloop.control.domain.TaskState;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RunnerDispatchServiceTest {
    private final DeliveryTaskRepository tasks = Mockito.mock(DeliveryTaskRepository.class);
    private final RunnerDispatchService dispatch = new RunnerDispatchService(tasks);

    @Test
    void returnsOnlyTasksSupportedByRunnerCapabilities() {
        FeatureRun run = new FeatureRun("org/repository", "main", "title", "spec", 1, "GENERIC", 1);
        run.addTask("IMPLEMENTATION", "Implement", "provider");
        run.addTask("INDEPENDENT_TEST", "Verify", "docker");
        Runner runner = new Runner("org", "verifier", "1", List.of("git", "docker"), "credential-hash");
        when(tasks.findByStateIn(List.of(TaskState.PENDING, TaskState.REPAIR_QUEUED))).thenReturn(run.getTasks());
        when(tasks.findByStateIn(List.of(TaskState.LEASED, TaskState.PREPARING, TaskState.RUNNING))).thenReturn(List.of());

        assertEquals(List.of("INDEPENDENT_TEST"), dispatch.available(runner).stream().map(task -> task.getRole()).toList());
    }

    @Test
    void blocksDependenciesAndOverlappingPathsWhileAllowingIndependentWork() {
        FeatureRun run = new FeatureRun("org/repository", "main", "title", "spec", 10, "GENERIC", 1);
        var active = run.addPlannedTask("backend", "BACKEND", "Backend", "provider", List.of("src/api"), 2, 1_000_000);
        var conflicting = run.addPlannedTask("api-test", "INDEPENDENT_TEST", "API test", "provider", List.of("src/api/tests"), 2, 1_000_000);
        var dependent = run.addPlannedTask("review", "REVIEW", "Review", "provider", List.of("docs"), 2, 1_000_000);
        var independent = run.addPlannedTask("frontend", "FRONTEND", "Frontend", "provider", List.of("frontend"), 2, 1_000_000);
        dependent.dependsOn(active);
        active.transition(TaskState.LEASED);
        Runner runner = new Runner("org", "worker", "1", List.of("provider"), "credential-hash");
        when(tasks.findByStateIn(List.of(TaskState.LEASED, TaskState.PREPARING, TaskState.RUNNING))).thenReturn(List.of(active));
        when(tasks.findByStateIn(List.of(TaskState.PENDING, TaskState.REPAIR_QUEUED))).thenReturn(List.of(conflicting, dependent, independent));

        assertEquals(List.of("frontend"), dispatch.available(runner).stream().map(task -> task.getPlanKey()).toList());
    }
}
