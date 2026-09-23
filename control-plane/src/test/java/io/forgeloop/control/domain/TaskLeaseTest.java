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
        task.transition(TaskState.LEASED);

        lease.acknowledge();
        lease.completeChangeReady("a".repeat(40));

        assertEquals(TaskState.CHANGE_READY, task.getState());
        assertEquals("a".repeat(40), task.getChangeSha());
    }

    @Test
    void failuresQueueOnlyTheOwningTaskAndExhaustionBlocksTheRun() {
        FeatureRun run = new FeatureRun("owner/repository", "issue-1", "Feature", "criterion", 5, "GENERIC", 1);
        DeliveryTask failing = run.addPlannedTask("backend", "BACKEND", "Backend", "git", List.of("src"), 1, 1_000_000);
        DeliveryTask independent = run.addPlannedTask("frontend", "FRONTEND", "Frontend", "git", List.of("web"), 1, 1_000_000);
        Runner runner = new Runner("organization", "runner", "1", List.of("git"), "credential-hash");

        failing.transition(TaskState.LEASED);
        TaskLease first = new TaskLease(failing, runner, "nonce-one", Instant.now().plusSeconds(60));
        first.acknowledge();
        first.complete(false);
        assertEquals(TaskState.REPAIR_QUEUED, failing.getState());
        assertEquals(TaskState.PENDING, independent.getState());

        failing.transition(TaskState.LEASED);
        TaskLease second = new TaskLease(failing, runner, "nonce-two", Instant.now().plusSeconds(60));
        second.acknowledge();
        second.complete(false);
        assertEquals(TaskState.FAILED, failing.getState());
        assertEquals(RunState.BLOCKED, run.getState());
    }

    @Test
    void retriesPreserveControlRolesAndOnlyConvertSourceWritersToRepair() {
        FeatureRun run = new FeatureRun("owner/repository", "issue-1", "Feature", "criterion", 5, "GENERIC", 1);
        DeliveryTask planner = run.addPlannedTask("planner", "PLANNER", "Plan", "provider", List.of(), 2, 1);
        DeliveryTask integration = run.addPlannedTask("integration", "INTEGRATION", "Integrate", "git", List.of(), 2, 1);
        DeliveryTask writer = run.addPlannedTask("writer", "BACKEND", "Write", "provider", List.of("src"), 2, 1);

        planner.transition(TaskState.LEASED);
        planner.transition(TaskState.REPAIR_QUEUED);
        integration.transition(TaskState.LEASED);
        integration.transition(TaskState.REPAIR_QUEUED);
        writer.transition(TaskState.LEASED);
        writer.transition(TaskState.REPAIR_QUEUED);

        assertEquals("PLANNER", planner.getExecutionRole());
        assertEquals("INTEGRATION", integration.getExecutionRole());
        assertEquals("REPAIR", writer.getExecutionRole());
    }

    @Test
    void integrationAdvancesOnlyDeclaredChangeReadyDependencies() {
        FeatureRun run = new FeatureRun("owner/repository", "issue-1", "Feature", "criterion", 5, "GENERIC", 1);
        DeliveryTask backend = run.addPlannedTask("backend", "BACKEND", "Backend", "git", List.of("src"), 2, 1);
        DeliveryTask frontend = run.addPlannedTask("frontend", "FRONTEND", "Frontend", "git", List.of("web"), 2, 1);
        DeliveryTask integration = run.addPlannedTask("integrate", "INTEGRATION", "Integrate", "git", List.of(), 2, 1);
        integration.dependsOn(backend);
        integration.dependsOn(frontend);
        backend.transition(TaskState.LEASED); backend.recordChangeSha("a".repeat(40)); backend.transition(TaskState.CHANGE_READY);
        frontend.transition(TaskState.LEASED); frontend.recordChangeSha("b".repeat(40)); frontend.transition(TaskState.CHANGE_READY);
        integration.transition(TaskState.LEASED);
        TaskLease lease = new TaskLease(integration, new Runner("org", "runner", "1", List.of("git"), "hash"),
                "nonce", Instant.now().plusSeconds(60));

        lease.acknowledge();
        lease.completeIntegration("c".repeat(40));

        assertEquals(TaskState.INTEGRATED, backend.getState());
        assertEquals(TaskState.INTEGRATED, frontend.getState());
        assertEquals(TaskState.INTEGRATED, integration.getState());
    }
}
