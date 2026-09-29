package io.forgeloop.control.application;

/** Runner-supplied pointer to the lease-bound bundle; verdict fields are never runner-authored. */
public record TestCheckEvidenceSubmission(String artifactReference, String bundleDigest) {
    public TestCheckEvidenceSubmission {
        if (artifactReference == null || !artifactReference.matches("artifact://[A-Za-z0-9_./-]{1,990}")
                || artifactReference.contains("..") || bundleDigest == null || !bundleDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Test-check evidence pointer is invalid");
        }
    }
}
