package io.forgeloop.control.application;

import java.time.Instant;
import java.util.List;

/** Immutable runner report for one verification command. */
public record VerificationEvidenceSubmission(
        String kind,
        String gate,
        String image,
        List<String> command,
        int exitCode,
        boolean timedOut,
        String output,
        Instant startedAt,
        Instant finishedAt,
        String artifactReference,
        String outputDigest,
        String bundleDigest) {
    public VerificationEvidenceSubmission {
        if (kind == null || kind.isBlank() || kind.length() > 80) throw new IllegalArgumentException("Evidence kind is required");
        if (gate != null && (gate.isBlank() || gate.length() > 120)) throw new IllegalArgumentException("Evidence gate is invalid");
        if (image != null && image.length() > 255) throw new IllegalArgumentException("Evidence image is too long");
        command = command == null ? List.of() : List.copyOf(command);
        if (command.isEmpty() || command.size() > 64 || command.stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 1000)) throw new IllegalArgumentException("Evidence command is required");
        if (output == null || output.length() > 65_536) throw new IllegalArgumentException("Evidence output exceeds the runner limit");
        if (startedAt == null || finishedAt == null || finishedAt.isBefore(startedAt)) throw new IllegalArgumentException("Evidence timestamps are invalid");
        if (artifactReference != null && !artifactReference.matches("[A-Za-z0-9_./:-]{1,1000}")) throw new IllegalArgumentException("Artifact reference is invalid");
        if (outputDigest == null || !outputDigest.matches("[0-9a-f]{64}") || bundleDigest == null || !bundleDigest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Evidence checksums are invalid");
    }
}
