package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/** Uploads only the current opted-in lease's complete, redacted, bounded journal segments. */
final class JournalUploader {
    static final int DEFAULT_SEGMENT_BYTES = 256 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final int maxCompressedBytes;

    JournalUploader() { this(readConfiguredLimit()); }

    JournalUploader(int maxCompressedBytes) {
        if (maxCompressedBytes < 128 || maxCompressedBytes > 1024 * 1024)
            throw new IllegalArgumentException("Run journal segment size is outside policy bounds");
        this.maxCompressedBytes = maxCompressedBytes;
    }

    void upload(RunnerTask task, RunnerIdentity identity, RunnerLease lease, RunnerClient client,
                StepJournal journal, RunnerEventReporter events) {
        if (task == null || !task.runRecord() || journal == null) return;
        try {
            List<JournalSegment> segments = segments(journal.rawLines(), lease.leaseId(), maxCompressedBytes);
            for (JournalSegment segment : segments) {
                String name = "journal-" + segment.firstSequence() + "-" + segment.lastSequence() + ".jsonl.gz";
                String digest = sha256(segment.compressed());
                Exception last = null;
                for (int attempt = 0; attempt < 2; attempt++) {
                    try {
                        client.uploadArtifact(identity, lease, segment.compressed(), digest,
                                "application/gzip", "RUN_JOURNAL", name);
                        last = null;
                        break;
                    } catch (Exception failure) { last = failure; }
                }
                if (last != null) throw last;
            }
        } catch (Exception failure) {
            events.warn("RECORD_UPLOAD_FAILED", "Run journal segment upload failed after retry");
        }
    }

    static List<JournalSegment> segments(List<String> rawLines, String leaseId, int maxCompressedBytes) throws IOException {
        if (rawLines == null || leaseId == null || leaseId.isBlank()) throw new IllegalArgumentException("Run journal upload identity is invalid");
        List<String> attemptLines = attemptLines(rawLines, leaseId);
        if (attemptLines.isEmpty()) return List.of();
        List<JournalSegment> result = new ArrayList<>();
        List<String> current = new ArrayList<>();
        long first = -1, last = -1;
        for (String original : attemptLines) {
            JsonNode record = JSON.readTree(original);
            long sequence = record.path("seq").asLong(-1);
            String candidateLine = redactLine(original, record);
            List<String> candidate = new ArrayList<>(current);
            candidate.add(candidateLine);
            if (!current.isEmpty() && gzip(candidate).length > maxCompressedBytes) {
                result.add(new JournalSegment(first, last, gzip(current)));
                current.clear(); first = -1;
                candidate = List.of(candidateLine);
            }
            if (gzip(candidate).length > maxCompressedBytes) {
                String omitted = omitted(record, original);
                if (gzip(List.of(omitted)).length > maxCompressedBytes)
                    throw new IllegalArgumentException("Run journal segment limit is too small for an omission marker");
                if (!current.isEmpty()) result.add(new JournalSegment(first, last, gzip(current)));
                result.add(new JournalSegment(sequence, sequence, gzip(List.of(omitted))));
                current.clear(); first = -1; last = -1;
                continue;
            }
            current.add(candidateLine);
            if (first < 0) first = sequence;
            last = sequence;
        }
        if (!current.isEmpty()) result.add(new JournalSegment(first, last, gzip(current)));
        return List.copyOf(result);
    }

    private static List<String> attemptLines(List<String> rawLines, String leaseId) throws IOException {
        List<String> selected = new ArrayList<>();
        boolean started = false;
        long startSequence = Long.MAX_VALUE;
        for (String line : rawLines) {
            JsonNode record = JSON.readTree(line);
            if (!leaseId.equals(record.path("leaseId").asText())) continue;
            String type = record.path("type").asText();
            if (!started && ("WORKER_STARTED".equals(type) || "LOOP_STARTED".equals(type))) {
                started = true;
                startSequence = record.path("seq").asLong(Long.MAX_VALUE);
            } else if (!started && "LOOP_RESUMED".equals(type) && record.path("resumedFromSeq").canConvertToLong()) {
                started = true;
                startSequence = record.path("resumedFromSeq").asLong();
            }
            if (started && record.path("seq").asLong(-1) >= startSequence) selected.add(line);
        }
        return List.copyOf(selected);
    }

    private static String redactLine(String original, JsonNode record) throws IOException {
        String redacted = EvidenceRedactor.redactTokens(original);
        if (redacted.equals(original)) return original;
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("redacted", true); wrapper.put("seq", record.path("seq").asLong());
        wrapper.put("leaseId", record.path("leaseId").asText()); wrapper.put("type", record.path("type").asText("UNKNOWN"));
        wrapper.put("sha256", sha256(original.getBytes(StandardCharsets.UTF_8))); wrapper.put("line", redacted);
        return JSON.writeValueAsString(wrapper);
    }

    private static String omitted(JsonNode record, String original) throws IOException {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("omitted", true); wrapper.put("seq", record.path("seq").asLong());
        wrapper.put("leaseId", record.path("leaseId").asText()); wrapper.put("type", record.path("type").asText("UNKNOWN"));
        wrapper.put("sha256", sha256(original.getBytes(StandardCharsets.UTF_8)));
        wrapper.put("bytes", original.getBytes(StandardCharsets.UTF_8).length);
        return JSON.writeValueAsString(wrapper);
    }

    private static byte[] gzip(List<String> lines) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            for (String line : lines) {
                gzip.write(line.getBytes(StandardCharsets.UTF_8));
                gzip.write('\n');
            }
        }
        return output.toByteArray();
    }

    private static int readConfiguredLimit() {
        String configured = System.getenv("FORGELOOP_RECORD_SEGMENT_BYTES");
        if (configured == null || configured.isBlank()) return DEFAULT_SEGMENT_BYTES;
        try { return Integer.parseInt(configured); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Run journal segment size is invalid", invalid); }
    }

    private static String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (Exception unavailable) { throw new IllegalStateException("SHA-256 is unavailable", unavailable); }
    }

    record JournalSegment(long firstSequence, long lastSequence, byte[] compressed) {
        JournalSegment { compressed = compressed.clone(); }
        @Override public byte[] compressed() { return compressed.clone(); }
    }
}
