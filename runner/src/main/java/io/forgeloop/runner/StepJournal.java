package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Forced, append-only local execution record. It may contain source and provider output. */
public final class StepJournal implements LoopJournal {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String FIRST_PREV = "0".repeat(64);
    private static final int MAX_RECOVERY_BYTES = 256 * 1024 * 1024;
    private final Path path;
    private final String leaseId;
    private final Clock clock;
    private long sequence;
    private String previousHash = FIRST_PREV;

    public StepJournal(Path stateRoot, String taskId, String leaseId, Clock clock) throws IOException {
        if (stateRoot == null || taskId == null || !taskId.matches("[A-Za-z0-9_-]{1,80}")
                || leaseId == null || leaseId.isBlank() || clock == null)
            throw new IllegalArgumentException("Step journal identity is invalid");
        Path stateDirectory = stateRoot.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(stateDirectory)) throw new IOException("Step journal state root cannot be a symbolic link");
        Files.createDirectories(stateDirectory);
        Path directory = stateDirectory.resolve("journals");
        if (Files.isSymbolicLink(directory)) throw new IOException("Step journal directory cannot be a symbolic link");
        Files.createDirectories(directory);
        restrictToOwner(directory, true);
        path = directory.resolve(taskId + ".jsonl");
        if (Files.isSymbolicLink(path)) throw new IOException("Step journal cannot be a symbolic link");
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
            try { Files.createFile(path, PosixFilePermissions.asFileAttribute(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))); }
            catch (UnsupportedOperationException unsupported) { Files.createFile(path); }
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Step journal path is not a regular file");
        restrictToOwner(path, false);
        this.leaseId = leaseId;
        this.clock = clock;
        recoverChain();
    }

    /** Appends one full local-only event and fsyncs it before returning. */
    public synchronized void append(String type, Map<String, ?> fields) throws IOException {
        if (type == null || !type.matches("[A-Z_]{1,80}") || fields == null
                || fields.keySet().stream().anyMatch(key -> List.of("seq", "at", "leaseId", "type", "prev").contains(key)))
            throw new IllegalArgumentException("Step journal record is invalid");
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("seq", sequence + 1);
        record.put("at", clock.instant().toString());
        record.put("leaseId", leaseId);
        record.put("type", type);
        record.put("prev", previousHash);
        record.putAll(fields);
        byte[] line = JSON.writeValueAsBytes(record);
        ByteBuffer bytes = ByteBuffer.allocate(line.length + 1).put(line).put((byte) '\n');
        bytes.flip();
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        sequence++;
        previousHash = Hashing.sha256(line);
    }

    public Path path() { return path; }

    /** Returns verified complete records; a non-newline-terminated final fragment is ignored. */
    public synchronized List<JsonNode> records() throws IOException {
        recoverChain();
        byte[] contents = Files.readAllBytes(path);
        int completeLength = lastNewline(contents) + 1;
        List<JsonNode> result = new ArrayList<>();
        int start = 0;
        while (start < completeLength) {
            int end = start;
            while (end < completeLength && contents[end] != '\n') end++;
            if (end > start) result.add(JSON.readTree(new String(contents, start, end - start, StandardCharsets.UTF_8)));
            start = end + 1;
        }
        return List.copyOf(result);
    }

    /** Returns original, hash-verified JSONL bytes for upload without reserializing any record. */
    public synchronized List<String> rawLines() throws IOException {
        recoverChain();
        byte[] contents = Files.readAllBytes(path);
        int completeLength = lastNewline(contents) + 1;
        List<String> lines = new ArrayList<>();
        int start = 0;
        while (start < completeLength) {
            int end = start;
            while (end < completeLength && contents[end] != '\n') end++;
            if (end > start) lines.add(new String(contents, start, end - start, StandardCharsets.UTF_8));
            start = end + 1;
        }
        return List.copyOf(lines);
    }

    private void recoverChain() throws IOException {
        long size = Files.size(path);
        if (size > MAX_RECOVERY_BYTES) throw new IOException("Step journal exceeds the safe recovery limit");
        byte[] contents = Files.readAllBytes(path);
        int completeLength = lastNewline(contents) + 1;
        int start = 0;
        String expectedPrevious = FIRST_PREV;
        long expectedSequence = 1;
        while (start < completeLength) {
            int end = start;
            while (end < completeLength && contents[end] != '\n') end++;
            byte[] line = java.util.Arrays.copyOfRange(contents, start, end);
            JsonNode record = JSON.readTree(line);
            if (record == null || record.path("seq").asLong(-1) != expectedSequence
                    || !record.path("prev").asText().equals(expectedPrevious))
                throw new IOException("Step journal hash chain is invalid");
            expectedPrevious = Hashing.sha256(line);
            expectedSequence++;
            start = end + 1;
        }
        if (completeLength < contents.length) {
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
                channel.truncate(completeLength);
                channel.force(true);
            }
        }
        sequence = expectedSequence - 1;
        previousHash = expectedPrevious;
    }

    private static int lastNewline(byte[] contents) {
        for (int index = contents.length - 1; index >= 0; index--) if (contents[index] == '\n') return index;
        return -1;
    }

    private static void restrictToOwner(Path target, boolean directory) throws IOException {
        try {
            Files.setPosixFilePermissions(target, directory
                    ? EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
                    : EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows ACLs are managed by the signed-in user and host policy.
        }
    }
}
