package io.forgeloop.control.application;

import io.forgeloop.control.domain.TestCheckRules;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Bounded report payload uploaded as a checksummed artifact and revalidated by the control plane. */
public record TestCheckBundle(String checkKind, String gate, String image, List<String> command,
                              String targetSha, String parentSha, TestCheckRunBundle before,
                              TestCheckRunBundle after, List<TestCheckRules.ChangedFile> changedFiles) {
    public TestCheckBundle {
        if (checkKind == null || !List.of("RED", "GREEN").contains(checkKind) || gate == null || gate.isBlank() || gate.length() > 80
                || image == null || image.isBlank() || image.length() > 255
                || command == null || command.isEmpty() || command.size() > 64
                || command.stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 1000)
                || command.stream().mapToInt(String::length).sum() + command.size() - 1 > 8_000
                || targetSha == null || !targetSha.matches("[0-9a-f]{40,64}")) {
            throw new IllegalArgumentException("Test check bundle metadata is invalid");
        }
        command = List.copyOf(command);
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        if (changedFiles.size() > 400) throw new IllegalArgumentException("Test check bundle has too many changed files");
        if ("RED".equals(checkKind)) {
            if (before == null || after == null || parentSha == null || !parentSha.matches("[0-9a-f]{40,64}") || changedFiles.isEmpty()) {
                throw new IllegalArgumentException("RED check bundle must contain both runs and its parent commit");
            }
        } else if (before != null || parentSha != null || !changedFiles.isEmpty() || after == null) {
            throw new IllegalArgumentException("GREEN check bundle contains invalid parent-run data");
        }
    }

    public record TestCheckRunBundle(TestCheckRules.ReportStatus status, int exitCode, boolean timedOut,
                                     Map<String, TestCheckRules.Outcome> outcomes, String outputDigest,
                                     String outcomeDigest, Instant startedAt, Instant finishedAt) {
        public TestCheckRunBundle {
            if (status == null || outcomes == null || outcomes.size() > 50_000 || exitCode < -1
                    || outputDigest == null || !outputDigest.matches("[0-9a-f]{64}")
                    || startedAt == null || finishedAt == null || finishedAt.isBefore(startedAt)) {
                throw new IllegalArgumentException("Test check run report is invalid");
            }
            outcomes = Map.copyOf(new java.util.TreeMap<>(outcomes));
            if (status != TestCheckRules.ReportStatus.READ && !outcomes.isEmpty()) {
                throw new IllegalArgumentException("Unavailable test report cannot contain outcomes");
            }
            if (outcomes.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getKey().length() > 500 || entry.getKey().codePoints().anyMatch(Character::isISOControl)
                    || entry.getValue() == null)) {
                throw new IllegalArgumentException("Test identity is invalid");
            }
            String calculated = TestCheckBundle.outcomeDigest(outcomes);
            if (outcomeDigest == null || !outcomeDigest.equals(calculated)) {
                throw new IllegalArgumentException("Test outcome digest does not match the report");
            }
        }

        public TestCheckRules.RunReport toRulesReport() {
            return new TestCheckRules.RunReport(status, exitCode, timedOut, outcomes);
        }
    }

    public static String outcomeDigest(Map<String, TestCheckRules.Outcome> outcomes) {
        String canonical = new java.util.TreeMap<>(outcomes).entrySet().stream()
                .map(entry -> entry.getKey() + "\u0000" + entry.getValue().name())
                .reduce((left, right) -> left + "\n" + right).orElse("");
        return sha256(canonical);
    }

    public static String sha256(String material) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    public static String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }
}
