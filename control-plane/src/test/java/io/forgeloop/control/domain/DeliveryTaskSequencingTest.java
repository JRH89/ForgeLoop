package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DeliveryTaskSequencingTest {
    @Test
    void chainedWriterWaitsForChangeReadyAndStartsFromItsDependencyCommit() {
        FeatureRun run = run();
        DeliveryTask tests = writer(run, "tests", "INDEPENDENT_TEST", "tests");
        DeliveryTask implementation = writer(run, "implementation", "IMPLEMENTATION", "src");
        DeliveryTask redCheck = run.addPlannedTask("red-tests", "RED_CHECK", "Check tests first", "docker", List.of(), 2, 0);
        implementation.dependsOn(tests);
        implementation.dependsOn(redCheck);
        redCheck.dependsOn(tests);

        assertFalse(implementation.dependenciesSatisfied());
        tests.transition(TaskState.LEASED);
        tests.recordChangeSha(sha('a'));
        tests.transition(TaskState.CHANGE_READY);
        assertFalse(implementation.dependenciesSatisfied());
        redCheck.transition(TaskState.LEASED);
        redCheck.transition(TaskState.VERIFIED);

        assertTrue(implementation.dependenciesSatisfied());
        assertEquals(sha('a'), implementation.getExecutionBaseRef());
    }

    @Test
    void chainedWriterRequiresACommitAndRetryKeepsItsDependencyBaseWhileQualityRepairUsesIntegration() {
        FeatureRun run = run();
        DeliveryTask tests = writer(run, "tests", "INDEPENDENT_TEST", "tests");
        DeliveryTask implementation = writer(run, "implementation", "IMPLEMENTATION", "src");
        DeliveryTask integration = run.addPlannedTask("integration", "INTEGRATION", "Integrate", "git", List.of(), 2, 0);
        DeliveryTask repair = writer(run, "repair", "REPAIR", "src");
        implementation.dependsOn(tests);
        tests.transition(TaskState.LEASED);
        tests.transition(TaskState.CHANGE_READY);

        assertThrows(IllegalStateException.class, implementation::getExecutionBaseRef);
        tests.recordChangeSha(sha('a'));
        implementation.transition(TaskState.LEASED);
        implementation.transition(TaskState.REPAIR_QUEUED);
        integration.recordChangeSha(sha('c'));

        assertEquals(sha('a'), implementation.getExecutionBaseRef());
        assertEquals(sha('c'), repair.getExecutionBaseRef());
    }

    @Test
    void integrationOrdersChainedCommitsByDependencyAndKeepsRepairCommitsLast() {
        FeatureRun run = run();
        DeliveryTask implementation = writer(run, "implementation", "IMPLEMENTATION", "src");
        DeliveryTask tests = writer(run, "tests", "INDEPENDENT_TEST", "tests");
        DeliveryTask repair = writer(run, "quality-repair", "REPAIR", "src");
        DeliveryTask integration = run.addPlannedTask("integration", "INTEGRATION", "Integrate", "git", List.of(), 2, 0);
        implementation.dependsOn(tests);
        implementation.recordChangeSha(sha('b'));
        tests.recordChangeSha(sha('a'));
        repair.recordChangeSha(sha('c'));
        integration.dependsOn(implementation);
        integration.dependsOn(tests);
        integration.dependsOn(repair);

        assertEquals(List.of(sha('a'), sha('b'), sha('c')), integration.getDependencyChangeShas());
    }

    @Test
    void independentWritersKeepTheirDeclaredIntegrationOrderAndReviewWaitsForIntegration() {
        FeatureRun run = run();
        DeliveryTask backend = writer(run, "backend", "BACKEND", "backend");
        DeliveryTask frontend = writer(run, "frontend", "FRONTEND", "frontend");
        DeliveryTask integration = run.addPlannedTask("integration", "INTEGRATION", "Integrate", "git", List.of(), 2, 0);
        DeliveryTask review = run.addPlannedTask("review", "REVIEW", "Review", "provider", List.of(), 2, 0);
        backend.recordChangeSha(sha('a'));
        frontend.recordChangeSha(sha('b'));
        integration.dependsOn(frontend);
        integration.dependsOn(backend);
        review.dependsOn(frontend);
        frontend.transition(TaskState.LEASED);
        frontend.transition(TaskState.CHANGE_READY);

        assertEquals(List.of(sha('b'), sha('a')), integration.getDependencyChangeShas());
        assertFalse(review.dependenciesSatisfied());
    }

    @Test
    void testFirstIntegrationWaitsForRedChecksAsWellAsWriterCommits() {
        FeatureRun run = run();
        run.snapshotTestFirst("unit", List.of("**/src/test/**"));
        run.addGate("unit");
        DeliveryTask tests = writer(run, "tests", "INDEPENDENT_TEST", "src/test");
        DeliveryTask implementation = writer(run, "implementation", "IMPLEMENTATION", "src/main");
        DeliveryTask integration = run.addPlannedTask("integration", "INTEGRATION", "Integrate", "git", List.of(), 2, 0);
        implementation.dependsOn(tests);
        integration.dependsOn(implementation);

        run.addTestCheckTasks();
        DeliveryTask red = run.getTasks().stream().filter(task -> "RED_CHECK".equals(task.getRole())).findFirst().orElseThrow();
        tests.recordChangeSha(sha('a'));
        tests.transition(TaskState.LEASED);
        tests.transition(TaskState.CHANGE_READY);

        assertTrue(red.dependenciesSatisfied());
        assertFalse(implementation.dependenciesSatisfied(), "implementation cannot start before RED passes");
        assertFalse(integration.dependenciesSatisfied(), "integration cannot pass a pending RED task");
        red.transition(TaskState.LEASED);
        red.transition(TaskState.VERIFIED);
        assertTrue(implementation.dependenciesSatisfied());
        implementation.transition(TaskState.LEASED);
        implementation.recordChangeSha(sha('b'));
        implementation.transition(TaskState.CHANGE_READY);

        assertTrue(integration.dependenciesSatisfied());
        assertEquals(List.of(sha('a'), sha('b')), integration.getDependencyChangeShas());
    }

    private static FeatureRun run() {
        return new FeatureRun("org", "org/repository", "issue-1", "feature", "spec", 5, "GENERIC", "main", 1);
    }

    private static DeliveryTask writer(FeatureRun run, String key, String role, String path) {
        return run.addPlannedTask(key, role, key, "provider", List.of(path), 2, 1_000_000);
    }

    private static String sha(char digit) {
        return String.valueOf(digit).repeat(40);
    }
}
