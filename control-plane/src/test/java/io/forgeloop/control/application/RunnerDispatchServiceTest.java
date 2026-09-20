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

        assertEquals(List.of("INDEPENDENT_TEST"), dispatch.available(runner).stream().map(task -> task.getRole()).toList());
    }
}
