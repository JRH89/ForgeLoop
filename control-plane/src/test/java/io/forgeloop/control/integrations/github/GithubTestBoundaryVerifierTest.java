package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.TestCheckEvidence;
import io.forgeloop.control.domain.TestCheckRules;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GithubTestBoundaryVerifierTest {
    private static final String APPROVED_BLOB = "a".repeat(40);
    private final GithubTestBoundaryVerifier verifier = new GithubTestBoundaryVerifier();

    @Test
    void acceptsOnlyTheCurrentRedCheckedTestBlob() {
        FeatureRun run = run(evidence(List.of(new TestCheckRules.ChangedFile("tests/NewTest.java", APPROVED_BLOB))));

        Optional<String> violation = verifier.firstViolation(run,
                List.of(new GithubChangedFile("tests/NewTest.java", "modified", APPROVED_BLOB)));

        assertTrue(violation.isEmpty());
    }

    @Test
    void rejectsUnverifiedBlobsAndRemovedTests() {
        FeatureRun run = run(evidence(List.of(new TestCheckRules.ChangedFile("tests/NewTest.java", APPROVED_BLOB))));

        assertEquals(Optional.of("tests/NewTest.java"), verifier.firstViolation(run,
                List.of(new GithubChangedFile("tests/NewTest.java", "modified", "b".repeat(40)))));
        assertEquals(Optional.of("tests/NewTest.java"), verifier.firstViolation(run,
                List.of(new GithubChangedFile("tests/NewTest.java", "removed", null))));
    }

    @Test
    void failsClosedWhenEvidenceIsIncompleteConflictingOrCompareIsCapped() {
        FeatureRun incomplete = run();
        assertEquals(Optional.of("Current passing RED evidence is incomplete"),
                verifier.firstViolation(incomplete, List.of()));
        when(incomplete.getTestPathGlobs()).thenReturn(List.of("../tests/**"));
        assertEquals(Optional.of("The run's test-path boundary is missing or invalid"),
                verifier.firstViolation(incomplete, List.of()));

        FeatureRun conflicting = run(
                evidence(List.of(new TestCheckRules.ChangedFile("tests/NewTest.java", APPROVED_BLOB))),
                evidence(List.of(new TestCheckRules.ChangedFile("tests/NewTest.java", "b".repeat(40)))));
        assertEquals(Optional.of("Current RED evidence contains conflicting blobs for tests/NewTest.java"),
                verifier.firstViolation(conflicting, List.of()));

        assertEquals(Optional.of("GitHub compare reached its 300-file completeness limit"),
                verifier.firstViolation(incomplete,
                        java.util.Collections.nCopies(GithubTestBoundaryVerifier.MAX_COMPARE_FILES,
                                new GithubChangedFile("src/App.java", "modified", APPROVED_BLOB))));
    }

    @Test
    void ignoresNonTestFilesButRequiresACompleteRedSet() {
        FeatureRun run = run(evidence(List.of(new TestCheckRules.ChangedFile("tests/NewTest.java", APPROVED_BLOB))));

        assertTrue(verifier.firstViolation(run,
                List.of(new GithubChangedFile("src/App.java", "modified", "b".repeat(40)))).isEmpty());
    }

    private static FeatureRun run(TestCheckEvidence... evidence) {
        FeatureRun run = mock(FeatureRun.class);
        when(run.getTestPathGlobs()).thenReturn(List.of("tests/**"));
        when(run.currentRedEvidence()).thenReturn(List.of(evidence));
        List<DeliveryTask> checks = java.util.stream.IntStream.range(0, evidence.length)
                .mapToObj(index -> {
                    DeliveryTask check = mock(DeliveryTask.class);
                    when(check.getRole()).thenReturn("RED_CHECK");
                    return check;
                }).toList();
        when(run.getTasks()).thenReturn(checks);
        return run;
    }

    private static TestCheckEvidence evidence(List<TestCheckRules.ChangedFile> changedFiles) {
        TestCheckEvidence evidence = mock(TestCheckEvidence.class);
        when(evidence.getKind()).thenReturn("RED");
        when(evidence.getVerdict()).thenReturn("PASS");
        when(evidence.changedFilesEvidence()).thenReturn(changedFiles);
        return evidence;
    }
}
