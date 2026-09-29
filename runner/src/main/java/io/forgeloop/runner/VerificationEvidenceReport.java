package io.forgeloop.runner;

import java.util.List;

/** Validated, checksummed, redacted report sent to the control plane after a verification attempt. */
public record VerificationEvidenceReport(String kind, String gate, String image, List<String> command, VerificationResult result,
                                         String artifactReference, String outputDigest, String bundleDigest,
                                         String targetSha, String imageId) {
    public VerificationEvidenceReport(String kind, String gate, String image, String command, VerificationResult result) {
        this(kind, gate, image, List.of(command), result, null, null, null, null, null);
    }
    public VerificationEvidenceReport(String kind, String gate, String image, List<String> command, VerificationResult result, String artifactReference) {
        this(kind, gate, image, command, result, artifactReference, null, null, null, null);
    }
    public VerificationEvidenceReport(String kind, String gate, String image, List<String> command, VerificationResult result,
                                      String artifactReference, String outputDigest, String bundleDigest) {
        this(kind, gate, image, command, result, artifactReference, outputDigest, bundleDigest, null, null);
    }
    public VerificationEvidenceReport {
        if (kind == null || kind.isBlank() || kind.length() > 80) throw new IllegalArgumentException("Evidence kind is required");
        if (gate != null && (gate.isBlank() || gate.length() > 120)) throw new IllegalArgumentException("Evidence gate is invalid");
        if (image != null && image.length() > 255) throw new IllegalArgumentException("Evidence image is too long");
        command = command == null ? List.of() : List.copyOf(command);
        if (command.isEmpty() || command.size() > 64 || command.stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 1000)) throw new IllegalArgumentException("Evidence command is required");
        if (result == null) throw new IllegalArgumentException("Verification result is required");
        if (targetSha != null && !targetSha.matches("[0-9a-f]{40,64}")) throw new IllegalArgumentException("Evidence target SHA is invalid");
        if (imageId != null && !imageId.matches("sha256:[0-9a-f]{64}")) throw new IllegalArgumentException("Evidence image ID is invalid");
        String redacted = EvidenceRedactor.redact(result.output());
        result = new VerificationResult(result.exitCode(), result.timedOut(), redacted, result.startedAt(), result.finishedAt(), result.outputTruncated());
        outputDigest = EvidenceDigests.sha256(redacted);
        bundleDigest = EvidenceDigests.bundle(kind, gate, image, command, result, artifactReference, outputDigest);
    }
}
