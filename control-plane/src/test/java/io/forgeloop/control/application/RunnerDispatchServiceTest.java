package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import io.forgeloop.control.domain.DeliveryTaskRepository;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.Runner;
import io.forgeloop.control.domain.TaskLease;
import io.forgeloop.control.domain.TaskLeaseRepository;
import io.forgeloop.control.domain.TaskState;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;

class RunnerDispatchServiceTest {
    private final DeliveryTaskRepository tasks = Mockito.mock(DeliveryTaskRepository.class);
    private final TaskLeaseRepository leases = mock(TaskLeaseRepository.class);
    private final RunnerDispatchService dispatch = new RunnerDispatchService(tasks, new ChainedWriterRunnerAffinity(leases));

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

    @Test
    void offersAChainedWriterOnlyToTheRunnerThatProducedItsDependency() {
        FeatureRun run = new FeatureRun("org/repository", "main", "feature", "spec", 5, "GENERIC", 1);
        var predecessor = run.addPlannedTask("tests", "INDEPENDENT_TEST", "Tests", "provider", List.of("tests"), 2, 1_000_000);
        var dependent = run.addPlannedTask("implementation", "IMPLEMENTATION", "Implementation", "provider", List.of("src"), 2, 1_000_000);
        var redCheck = run.addPlannedTask("red-tests", "RED_CHECK", "Check tests first", "docker", List.of(), 2, 0);
        dependent.dependsOn(predecessor);
        dependent.dependsOn(redCheck);
        redCheck.dependsOn(predecessor);
        predecessor.transition(TaskState.LEASED);
        predecessor.recordChangeSha("a".repeat(40));
        predecessor.transition(TaskState.CHANGE_READY);
        redCheck.transition(TaskState.LEASED);
        redCheck.transition(TaskState.VERIFIED);
        TaskLease producerLease = mock(TaskLease.class);
        when(producerLease.getRunnerId()).thenReturn("runner-a");
        when(leases.findFirstByTask_IdOrderByExpiresAtDesc(predecessor.getId())).thenReturn(Optional.of(producerLease));
        when(tasks.findByStateIn(List.of(TaskState.PENDING, TaskState.REPAIR_QUEUED))).thenReturn(List.of(dependent));
        when(tasks.findByStateIn(List.of(TaskState.LEASED, TaskState.PREPARING, TaskState.RUNNING))).thenReturn(List.of());
        Runner producer = mock(Runner.class);
        when(producer.getId()).thenReturn("runner-a");
        when(producer.hasCapability("provider")).thenReturn(true);
        Runner other = mock(Runner.class);
        when(other.getId()).thenReturn("runner-b");
        when(other.hasCapability("provider")).thenReturn(true);

        assertEquals(List.of("implementation"), dispatch.available(producer).stream().map(task -> task.getPlanKey()).toList());
        assertEquals(List.of(), dispatch.available(other));
    }
}
