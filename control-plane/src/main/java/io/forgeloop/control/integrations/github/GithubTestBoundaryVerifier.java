package io.forgeloop.control.integrations.github;

import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.TestCheckEvidence;
import io.forgeloop.control.domain.TestCheckRules;
import io.forgeloop.control.domain.TestPathGlobs;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Compares GitHub's published test blobs with the run's current, passing RED evidence. */
public final class GithubTestBoundaryVerifier {
    public static final int MAX_COMPARE_FILES = 300;

    public Optional<String> firstViolation(FeatureRun run, List<GithubChangedFile> comparedFiles) {
        if (comparedFiles == null) return Optional.of("GitHub compare did not return a file list");
        if (comparedFiles.size() >= MAX_COMPARE_FILES) {
            return Optional.of("GitHub compare reached its 300-file completeness limit");
        }
        if (!TestPathGlobs.areValid(run.getTestPathGlobs())) {
            return Optional.of("The run's test-path boundary is missing or invalid");
        }

        List<TestCheckEvidence> currentRed = run.currentRedEvidence();
        long expectedRedChecks = run.getTasks().stream().filter(task -> "RED_CHECK".equals(task.getRole())).count();
        if (expectedRedChecks == 0 || currentRed.size() != expectedRedChecks) {
            return Optional.of("Current passing RED evidence is incomplete");
        }

        Map<String, String> expectedBlobs = new HashMap<>();
        try {
            for (TestCheckEvidence evidence : currentRed) {
                if (!"RED".equals(evidence.getKind()) || !"PASS".equals(evidence.getVerdict())) {
                    return Optional.of("Current RED evidence is not passing");
                }
                for (TestCheckRules.ChangedFile changed : evidence.changedFilesEvidence()) {
                    if (!TestPathGlobs.matchesAny(changed.path(), run.getTestPathGlobs())) continue;
                    String priorBlob = expectedBlobs.putIfAbsent(changed.path(), changed.blobSha());
                    if (priorBlob != null && !priorBlob.equals(changed.blobSha())) {
                        return Optional.of("Current RED evidence contains conflicting blobs for " + changed.path());
                    }
                }
            }
        } catch (IllegalArgumentException malformedEvidence) {
            return Optional.of("Current RED evidence contains an invalid changed-file record");
        }

        for (GithubChangedFile changed : comparedFiles) {
            if (!TestPathGlobs.matchesAny(changed.path(), run.getTestPathGlobs())) continue;
            if ("removed".equals(changed.status())) {
                return Optional.of(changed.path());
            }
            if (!changed.blobSha().equals(expectedBlobs.get(changed.path()))) {
                return Optional.of(changed.path());
            }
        }
        return Optional.empty();
    }
}
