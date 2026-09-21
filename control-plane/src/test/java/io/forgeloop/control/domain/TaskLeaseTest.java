package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskLeaseTest {
    @Test
    void providerCompletionProducesChangeReadyRatherThanVerified() {
        FeatureRun run = new FeatureRun("owner/repository", "issue-1", "Feature", "criterion", 5, "GENERIC", 1);
        run.addTask("IMPLEMENTATION", "Implement feature", "git");
        DeliveryTask task = run.getTasks().getFirst();
        Runner runner = new Runner("organization", "runner", "1", List.of("git"), "credential-hash");
        TaskLease lease = new TaskLease(task, runner, "nonce-hash", Instant.now().plusSeconds(60));

        lease.acknowledge();
        lease.completeChangeReady();

        assertEquals(TaskState.CHANGE_READY, task.getState());
    }
}
