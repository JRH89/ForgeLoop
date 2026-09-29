package io.forgeloop.runner;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Canonical, bounded artifact shape consumed and independently revalidated by the control plane. */
public record TestCheckArtifact(String checkKind, String gate, String image, List<String> command,
                                String targetSha, String parentSha, TestCheckRun before,
                                TestCheckRun after, List<GitWorktreeManager.ChangedFile> changedFiles) {
    public static final int MAX_ARTIFACT_BYTES = 1024 * 1024;

    public TestCheckArtifact {
        command = List.copyOf(command);
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        if (checkKind == null || !List.of("RED", "GREEN").contains(checkKind) || targetSha == null
                || !targetSha.matches("[0-9a-f]{40,64}") || after == null
                || ("RED".equals(checkKind) != (before != null && parentSha != null && !changedFiles.isEmpty()))) {
            throw new IllegalArgumentException("Test-check artifact shape is invalid");
        }
        java.util.stream.Stream.of(before, after).filter(java.util.Objects::nonNull)
                .flatMap(report -> report.outcomes().keySet().stream()).forEach(TestCheckArtifact::requireSafeEvidenceText);
        changedFiles.forEach(file -> requireSafeEvidenceText(file.path()));
    }

    public static TestCheckRun from(VerificationResult result, TestRunReport report) {
        if (result == null || report == null) throw new IllegalArgumentException("Test-check run result is required");
        String safeOutput = EvidenceRedactor.redact(result.output());
        return new TestCheckRun(report.status().name(), result.exitCode(), result.timedOut(), report.outcomes(),
                EvidenceDigests.sha256(safeOutput), outcomeDigest(report.outcomes()), result.startedAt().toString(), result.finishedAt().toString());
    }

    /** Keeps an oversized report in the evidence flow as UNREADABLE instead of retrying indefinitely. */
    public TestCheckArtifact withUnreadableReports() {
        return new TestCheckArtifact(checkKind, gate, image, command, targetSha, parentSha,
                before == null ? null : before.unreadable(), after.unreadable(), changedFiles);
    }

    private static String outcomeDigest(Map<String, TestRunReport.Outcome> outcomes) {
        String canonical = new TreeMap<>(outcomes).entrySet().stream()
                .map(entry -> entry.getKey() + "\u0000" + entry.getValue().name())
                .reduce((left, right) -> left + "\n" + right).orElse("");
        return EvidenceDigests.sha256(canonical);
    }

    private static void requireSafeEvidenceText(String value) {
        if (!EvidenceRedactor.redact(value).equals(value)) throw new IllegalArgumentException("Test report identity contains a possible secret");
    }

    public record TestCheckRun(String status, int exitCode, boolean timedOut,
                               Map<String, TestRunReport.Outcome> outcomes,
                               String outputDigest, String outcomeDigest,
                               String startedAt, String finishedAt) {
        public TestCheckRun {
            if (!List.of("READ", "MISSING", "UNREADABLE").contains(status)
                    || exitCode < -1 || outcomes == null || outcomes.size() > 50_000
                    || outputDigest == null || !outputDigest.matches("[0-9a-f]{64}")
                    || outcomeDigest == null || !outcomeDigest.matches("[0-9a-f]{64}")
                    || startedAt == null || finishedAt == null || Instant.parse(finishedAt).isBefore(Instant.parse(startedAt))) {
                throw new IllegalArgumentException("Test-check run report is invalid");
            }
            outcomes = Map.copyOf(new TreeMap<>(outcomes));
            if (!"READ".equals(status) && !outcomes.isEmpty()) {
                throw new IllegalArgumentException("Unavailable test report cannot contain outcomes");
            }
        }

        private TestCheckRun unreadable() {
            return new TestCheckRun("UNREADABLE", exitCode, timedOut, Map.of(), outputDigest,
                    TestCheckArtifact.outcomeDigest(Map.of()), startedAt, finishedAt);
        }
    }
}
