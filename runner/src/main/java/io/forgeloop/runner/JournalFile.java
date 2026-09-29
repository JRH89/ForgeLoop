package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads one local JSONL journal without normalizing record bytes used by its hash chain. */
public final class JournalFile {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_BYTES = 256 * 1024 * 1024;
    private static final String FIRST_PREVIOUS = "0".repeat(64);
    private final List<JsonNode> records;

    private JournalFile(List<JsonNode> records) { this.records = List.copyOf(records); }

    /** Loads a complete, hash-verified journal; an incomplete final fragment is ignored like StepJournal recovery. */
    public static JournalFile open(Path path) throws IOException {
        if (path == null || Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Run journal must be a regular non-symlink file");
        long size = Files.size(path);
        if (size > MAX_BYTES) throw new IOException("Run journal exceeds the safe read limit");
        byte[] bytes = Files.readAllBytes(path);
        int completeLength = lastNewline(bytes) + 1;
        List<JsonNode> records = new ArrayList<>();
        String previous = FIRST_PREVIOUS;
        String leaseId = null;
        long expectedSequence = 1;
        int start = 0;
        while (start < completeLength) {
            int end = start;
            while (end < completeLength && bytes[end] != '\n') end++;
            byte[] line = java.util.Arrays.copyOfRange(bytes, start, end);
            JsonNode record;
            try { record = JSON.readTree(line); }
            catch (Exception malformed) { throw new IOException("Run journal contains malformed JSON", malformed); }
            String recordLease = record == null ? "" : record.path("leaseId").asText("");
            String type = record == null ? "" : record.path("type").asText("");
            if (record == null || !record.isObject() || record.path("seq").asLong(-1) != expectedSequence
                    || !record.path("prev").asText().equals(previous) || recordLease.isBlank()
                    || !type.matches("[A-Z_]{1,80}") || leaseId != null && !leaseId.equals(recordLease))
                throw new IOException("Run journal hash chain is invalid");
            if (leaseId == null) leaseId = recordLease;
            records.add(record);
            previous = Hashing.sha256(line);
            expectedSequence++;
            start = end + 1;
        }
        if (records.isEmpty()) throw new IOException("Run journal contains no complete records");
        return new JournalFile(records);
    }

    /** Returns defensive copies so replay cannot mutate its source journal. */
    public List<JsonNode> records() {
        List<JsonNode> copies = new ArrayList<>(records.size());
        for (JsonNode record : records) copies.add(record.deepCopy());
        return List.copyOf(copies);
    }

    private static int lastNewline(byte[] bytes) {
        for (int i = bytes.length - 1; i >= 0; i--) if (bytes[i] == '\n') return i;
        return -1;
    }
}
