package io.forgeloop.control.application;

/** Immutable runner report for one verification command. */
public record VerificationEvidenceSubmission(
        String kind,
        String image,
        String command,
        int exitCode,
        boolean timedOut,
        String output) {
    public VerificationEvidenceSubmission {
        if (kind == null || kind.isBlank() || kind.length() > 80) throw new IllegalArgumentException("Evidence kind is required");
        if (image != null && image.length() > 255) throw new IllegalArgumentException("Evidence image is too long");
        if (command == null || command.isBlank() || command.length() > 4_000) throw new IllegalArgumentException("Evidence command is required");
        if (output == null || output.length() > 65_536) throw new IllegalArgumentException("Evidence output exceeds the runner limit");
    }
}
