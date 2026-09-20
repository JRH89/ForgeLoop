package io.forgeloop.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/** Writes an immutable-on-success local verification artifact and a checksum manifest for later upload. */
public final class EvidenceBundleWriter {
    public Path write(Path bundleDirectory, VerificationEvidenceReport report) throws IOException {
        Path normalized = bundleDirectory.toAbsolutePath().normalize();
        Files.createDirectories(normalized);
        String reportJson = json(report);
        String fileName = "verification-" + Instant.now().toEpochMilli() + ".json";
        Path target = normalized.resolve(fileName);
        Path temporary = Files.createTempFile(normalized, ".verification-", ".tmp");
        try {
            Files.writeString(temporary, reportJson, StandardCharsets.UTF_8);
            moveAtomically(temporary, target);
            Files.writeString(normalized.resolve(fileName + ".sha256"), sha256(reportJson) + "  " + fileName + System.lineSeparator(), StandardCharsets.UTF_8);
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException exception) { Files.move(source, target); }
    }
    private static String json(VerificationEvidenceReport report) {
        VerificationResult result = report.result();
        return "{\"kind\":\"" + escape(report.kind()) + "\",\"gate\":" + nullable(report.gate())
                + ",\"image\":" + nullable(report.image()) + ",\"command\":\"" + escape(report.command())
                + "\",\"exitCode\":" + result.exitCode() + ",\"timedOut\":" + result.timedOut()
                + ",\"startedAt\":\"" + result.startedAt() + "\",\"finishedAt\":\"" + result.finishedAt()
                + "\",\"output\":\"" + escape(result.output()) + "\"}";
    }
    private static String nullable(String value) { return value == null ? "null" : "\"" + escape(value) + "\""; }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"); }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
