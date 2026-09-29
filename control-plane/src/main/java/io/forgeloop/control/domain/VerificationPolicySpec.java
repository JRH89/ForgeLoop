package io.forgeloop.control.domain;

import java.util.List;

/** Immutable, repository-owned command policy copied into every feature run. */
public record VerificationPolicySpec(String name, String kind, String imageDigest, List<String> command,
                                     String networkPolicy, int timeoutSeconds, boolean required,
                                     String criterionCoverage, String testReport) {
    /** Preserves the original policy constructor for repositories without report collection. */
    public VerificationPolicySpec(String name, String kind, String imageDigest, List<String> command,
                                  String networkPolicy, int timeoutSeconds, boolean required,
                                  String criterionCoverage) {
        this(name, kind, imageDigest, command, networkPolicy, timeoutSeconds, required, criterionCoverage, null);
    }

    public VerificationPolicySpec {
        command = command == null ? List.of() : List.copyOf(command);
        if (name == null || !name.matches("[a-z0-9-]{1,80}")) throw new IllegalArgumentException("Verification gate name is invalid");
        if (kind == null || !kind.matches("CONTAINER|BROWSER|SECURITY|CONTRACT|COMPOSE")) throw new IllegalArgumentException("Verification kind is invalid");
        if (imageDigest == null || !imageDigest.matches("[^@\\s]+@sha256:[0-9a-f]{64}")) throw new IllegalArgumentException("Verification image must be pinned by SHA-256 digest");
        if (command.isEmpty() || command.size() > 64 || command.stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 1000)) throw new IllegalArgumentException("Verification command is invalid");
        if (!"NONE".equals(networkPolicy) && !"EGRESS".equals(networkPolicy)) throw new IllegalArgumentException("Verification network policy is invalid");
        if (timeoutSeconds < 1 || timeoutSeconds > 3600) throw new IllegalArgumentException("Verification timeout is outside policy bounds");
        if (!"ALL".equals(criterionCoverage)) throw new IllegalArgumentException("Only explicit ALL criterion coverage is currently supported");
        if (testReport != null && !"JUNIT_XML".equals(testReport)) throw new IllegalArgumentException("Verification test report format is invalid");
        if (testReport != null && command.stream().noneMatch(value -> value.contains("/forgeloop/test-report"))) {
            throw new IllegalArgumentException("A reporting verification command must write to /forgeloop/test-report");
        }
    }
}
