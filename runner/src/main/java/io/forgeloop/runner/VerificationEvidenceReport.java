package io.forgeloop.runner;

/** Validated, bounded report sent to the control plane after a verification attempt. */
public record VerificationEvidenceReport(String kind, String image, String command, VerificationResult result) {
    public VerificationEvidenceReport {
        if (kind == null || kind.isBlank() || kind.length() > 80) throw new IllegalArgumentException("Evidence kind is required");
        if (image != null && image.length() > 255) throw new IllegalArgumentException("Evidence image is too long");
        if (command == null || command.isBlank() || command.length() > 4_000) throw new IllegalArgumentException("Evidence command is required");
        if (result == null) throw new IllegalArgumentException("Verification result is required");
    }
}
