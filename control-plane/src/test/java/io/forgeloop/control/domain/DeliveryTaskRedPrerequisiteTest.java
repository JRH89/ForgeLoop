package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import io.forgeloop.control.application.ChainedWriterRunnerAffinity;
import io.forgeloop.control.application.RunnerDispatchService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeliveryTaskRedPrerequisiteTest {
    private static final String TEST_SHA = "a".repeat(40);
    private static final String CURRENT_DIGEST = "c".repeat(64);

    @Test
    void implementationGetsCurrentPassingEvidenceAndKeepsItOnRepairRetry() {
        TaskGraph graph = eligibleGraph(false);
        graph.redCheck().attachTestCheckEvidence(evidence(TEST_SHA, CURRENT_DIGEST, 2));

        RedPrerequisite prerequisite = graph.implementation().getRedPrerequisite();

        assertEquals(new RedPrerequisite(graph.testWriter().getId(), TEST_SHA, CURRENT_DIGEST), prerequisite);
        graph.implementation().hold();
        graph.implementation().retryByOperator();
        assertEquals("REPAIR", graph.implementation().getExecutionRole());
        assertEquals(prerequisite, graph.implementation().getRedPrerequisite());
    }

    @Test
    void selectsLatestCurrentEvidenceAndIgnoresEarlierTestCommit() {
        TaskGraph graph = eligibleGraph(false);
        graph.redCheck().attachTestCheckEvidence(evidence("b".repeat(40), "d".repeat(64), 1));
        graph.redCheck().attachTestCheckEvidence(evidence(TEST_SHA, "e".repeat(64), 2));
        graph.redCheck().attachTestCheckEvidence(evidence(TEST_SHA, CURRENT_DIGEST, 3));

        assertEquals(CURRENT_DIGEST, graph.implementation().getRedPrerequisite().evidenceDigest());
    }

    @Test
    void missingOrStaleProofRetainsThePrerequisiteButCannotAppearValid() {
        TaskGraph graph = eligibleGraph(false);
        assertEquals(new RedPrerequisite(graph.testWriter().getId(), null, null),
                graph.implementation().getRedPrerequisite());

        TaskGraph stale = eligibleGraph(false);
        stale.redCheck().attachTestCheckEvidence(evidence("b".repeat(40), "d".repeat(64), 1));
        assertEquals(new RedPrerequisite(stale.testWriter().getId(), null, null),
                stale.implementation().getRedPrerequisite());
    }

    @Test
    void dependencyOrderDoesNotChangeProofAndDerivationDoesNotUseExecutionBaseHelper() {
        TaskGraph ordinaryOrder = eligibleGraph(false);
        ordinaryOrder.redCheck().attachTestCheckEvidence(evidence(TEST_SHA, CURRENT_DIGEST, 1));
        RedPrerequisite before = ordinaryOrder.implementation().getRedPrerequisite();

        TaskGraph reversedOrder = eligibleGraph(true);
        reversedOrder.redCheck().attachTestCheckEvidence(evidence(TEST_SHA, CURRENT_DIGEST, 1));
        DeliveryTask implementation = spy(reversedOrder.implementation());
        doThrow(new AssertionError("RED proof must be derived independently of execution-base selection"))
                .when(implementation).getWritingDependency();

        assertEquals(before, implementation.getRedPrerequisite());
        verify(implementation, never()).getWritingDependency();

        TaskGraph missingCheck = eligibleGraph(false);
        DeliveryTask writerWithoutCheck = missingCheck.run().addPlannedTask(
                "other", "BACKEND", "Other", "provider", List.of("src/other"), 2, 1_000_000);
        writerWithoutCheck.dependsOn(missingCheck.testWriter());
        assertEquals(new RedPrerequisite(missingCheck.testWriter().getId(), null, null),
                writerWithoutCheck.getRedPrerequisite());
    }

    @Test
    void onlyPairedTestFirstImplementationRolesReceiveThePrerequisite() {
        TaskGraph graph = eligibleGraph(false);
        DeliveryTask scaffold = graph.run().addPlannedTask(
                "scaffold", "IMPLEMENTATION", "Scaffold", "provider", List.of("src/base"), 2, 1_000_000);
        DeliveryTask independentTest = graph.run().addPlannedTask(
                "another-test", "INDEPENDENT_TEST", "Tests", "provider", List.of("tests/other"), 2, 1_000_000);
        DeliveryTask repair = graph.run().addPlannedTask(
                "repair", "REPAIR", "Quality repair", "provider", List.of("src"), 2, 1_000_000);

        assertNull(scaffold.getRedPrerequisite());
        assertNull(independentTest.getRedPrerequisite());
        assertNull(repair.getRedPrerequisite());

        FeatureRun ordinary = new FeatureRun("org", "org/repo", "issue-2", "ordinary", "spec", 5, "GENERIC", "main", 1);
        ordinary.addPlannedTask("implementation", "IMPLEMENTATION", "Implementation", "provider", List.of("src"), 2, 1_000_000);
        assertNull(ordinary.getTasks().getFirst().getRedPrerequisite());
    }

    @Test
    void dispatchMaterializesTheDerivedValueWhileLazyEvidenceIsAvailable() {
        TaskGraph graph = eligibleGraph(false);
        graph.testWriter().transition(TaskState.LEASED);
        graph.testWriter().transition(TaskState.CHANGE_READY);
        graph.redCheck().transition(TaskState.LEASED);
        graph.redCheck().transition(TaskState.VERIFIED);
        graph.redCheck().attachTestCheckEvidence(evidence(TEST_SHA, CURRENT_DIGEST, 1));

        DeliveryTaskRepository tasks = mock(DeliveryTaskRepository.class);
        TaskLeaseRepository leases = mock(TaskLeaseRepository.class);
        TaskLease producerLease = mock(TaskLease.class);
        when(producerLease.getRunnerId()).thenReturn("runner-a");
        when(leases.findFirstByTask_IdOrderByExpiresAtDesc(graph.testWriter().getId())).thenReturn(Optional.of(producerLease));
        when(tasks.findByStateIn(List.of(TaskState.PENDING, TaskState.REPAIR_QUEUED)))
                .thenReturn(List.of(graph.implementation()));
        when(tasks.findByStateIn(List.of(TaskState.LEASED, TaskState.PREPARING, TaskState.RUNNING)))
                .thenReturn(List.of());
        Runner runner = mock(Runner.class);
        when(runner.getId()).thenReturn("runner-a");
        when(runner.hasCapability("provider")).thenReturn(true);

        List<DeliveryTask> dispatched = new RunnerDispatchService(tasks, new ChainedWriterRunnerAffinity(leases)).available(runner);

        assertEquals(1, dispatched.size());
        assertEquals(new RedPrerequisite(graph.testWriter().getId(), TEST_SHA, CURRENT_DIGEST),
                dispatched.getFirst().getRedPrerequisite());
    }

    private static TaskGraph eligibleGraph(boolean reversedDependencies) {
        FeatureRun run = new FeatureRun("org", "org/repo", "issue-1", "test-first", "spec", 5, "GENERIC", "main", 1);
        run.snapshotTestFirst("unit", List.of("**/*Test.java"));
        DeliveryTask testWriter = run.addPlannedTask("tests", "INDEPENDENT_TEST", "Tests", "provider", List.of("src/test"), 2, 1_000_000);
        DeliveryTask redCheck = run.addPlannedTask("red", "RED_CHECK", "RED", "docker", List.of(), 2, 0);
        DeliveryTask implementation = run.addPlannedTask("implementation", "IMPLEMENTATION", "Implementation", "provider", List.of("src/main"), 2, 1_000_000);
        testWriter.recordChangeSha(TEST_SHA);
        redCheck.dependsOn(testWriter);
        if (reversedDependencies) {
            implementation.dependsOn(redCheck);
            implementation.dependsOn(testWriter);
        } else {
            implementation.dependsOn(testWriter);
            implementation.dependsOn(redCheck);
        }
        return new TaskGraph(run, testWriter, redCheck, implementation);
    }

    private static TestCheckEvidence evidence(String targetSha, String digest, long second) {
        TestCheckEvidence evidence = mock(TestCheckEvidence.class);
        when(evidence.isCurrentRedFor(TEST_SHA)).thenReturn(targetSha.equals(TEST_SHA));
        when(evidence.getTargetSha()).thenReturn(targetSha);
        when(evidence.getDigest()).thenReturn(digest);
        when(evidence.recordedAtInstant()).thenReturn(Instant.ofEpochSecond(second));
        return evidence;
    }

    private record TaskGraph(FeatureRun run, DeliveryTask testWriter, DeliveryTask redCheck, DeliveryTask implementation) { }
}
