package io.forgeloop.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes an immutable-on-success local verification artifact and a checksum manifest for later upload. */
public final class EvidenceBundleWriter {
    public Path write(Path bundleDirectory, VerificationEvidenceReport report) throws IOException {
        Path normalized = bundleDirectory.toAbsolutePath().normalize();
        Files.createDirectories(normalized);
        String reportJson = json(report);
        String fileName = "verification-" + report.bundleDigest() + ".json";
        Path target = normalized.resolve(fileName);
        Path temporary = Files.createTempFile(normalized, ".verification-", ".tmp");
        try {
            Files.writeString(temporary, reportJson, StandardCharsets.UTF_8);
            moveAtomically(temporary, target);
            Files.writeString(normalized.resolve(fileName + ".sha256"), EvidenceDigests.sha256(reportJson) + "  " + fileName + System.lineSeparator(), StandardCharsets.UTF_8);
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Recomputes the manifest before an artifact is uploaded or cited. */
    public boolean verify(Path artifact) throws IOException {
        Path normalized = artifact.toAbsolutePath().normalize();
        Path manifest = normalized.resolveSibling(normalized.getFileName() + ".sha256");
        if (!Files.isRegularFile(normalized) || !Files.isRegularFile(manifest)) return false;
        String line = Files.readString(manifest, StandardCharsets.UTF_8).strip();
        String expectedSuffix = "  " + normalized.getFileName();
        if (!line.endsWith(expectedSuffix) || line.length() != 64 + expectedSuffix.length()) return false;
        return line.substring(0, 64).equals(EvidenceDigests.sha256(Files.readString(normalized, StandardCharsets.UTF_8)));
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException exception) { Files.move(source, target); }
    }
    private static String json(VerificationEvidenceReport report) {
        VerificationResult result = report.result();
        return "{\"kind\":\"" + escape(report.kind()) + "\",\"gate\":" + nullable(report.gate())
                + ",\"image\":" + nullable(report.image()) + ",\"command\":[" + report.command().stream().map(value -> "\"" + escape(value) + "\"").reduce((a,b)->a+","+b).orElse("") + "]"
                + ",\"exitCode\":" + result.exitCode() + ",\"timedOut\":" + result.timedOut()
                + ",\"startedAt\":\"" + result.startedAt() + "\",\"finishedAt\":\"" + result.finishedAt()
                + "\",\"artifactReference\":" + nullable(report.artifactReference()) + ",\"outputDigest\":\"" + report.outputDigest()
                + "\",\"bundleDigest\":\"" + report.bundleDigest() + "\",\"output\":\"" + escape(result.output()) + "\"}";
    }
    private static String nullable(String value) { return value == null ? "null" : "\"" + escape(value) + "\""; }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"); }
}
