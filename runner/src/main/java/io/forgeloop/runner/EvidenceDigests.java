package io.forgeloop.runner;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/** Canonical checksums shared by local evidence bundles and control-plane submissions. */
final class EvidenceDigests {
    private EvidenceDigests() { }
    static String sha256(String material) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
    static String bundle(String kind, String gate, String image, List<String> command, VerificationResult result, String artifactReference, String outputDigest) {
        return sha256(String.join("\u0000", kind, gate == null ? "" : gate, image == null ? "" : image,
                String.join("\u001f", command), Integer.toString(result.exitCode()), Boolean.toString(result.timedOut()), outputDigest,
                result.startedAt().toString(), result.finishedAt().toString(), artifactReference == null ? "" : artifactReference));
    }
}
