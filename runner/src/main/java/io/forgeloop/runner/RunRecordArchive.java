package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Reads an untrusted ZIP defensively before any domain checks inspect its contents. */
public final class RunRecordArchive {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final long MAX_ARCHIVE_BYTES = 256L * 1024 * 1024;
    private static final long MAX_ENTRY_BYTES = 64L * 1024 * 1024;
    private static final long MAX_TOTAL_EXPANDED_BYTES = 256L * 1024 * 1024;
    private static final int MAX_ENTRIES = 20_000;
    private final JsonNode record;
    private final Map<String, byte[]> entries;
    private final List<String> unsafeEntries;

    private RunRecordArchive(JsonNode record, Map<String, byte[]> entries, List<String> unsafeEntries) {
        this.record = record.deepCopy();
        Map<String, byte[]> copies = new HashMap<>();
        entries.forEach((path, bytes) -> copies.put(path, bytes.clone()));
        this.entries = Map.copyOf(copies);
        this.unsafeEntries = List.copyOf(unsafeEntries);
    }

    public static RunRecordArchive open(Path archive) throws IOException {
        if (archive == null || Files.isSymbolicLink(archive)
                || !Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Run-record archive must be a regular non-symlink file");
        if (Files.size(archive) > MAX_ARCHIVE_BYTES) throw new IOException("Run-record archive exceeds the safe read limit");
        Map<String, byte[]> files = new HashMap<>();
        List<String> unsafe = new ArrayList<>();
        long total = 0;
        int count = 0;
        try (InputStream input = Files.newInputStream(archive); ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; zip.closeEntry()) {
                if (++count > MAX_ENTRIES) throw new IOException("Run-record archive has too many entries");
                String name = entry.getName();
                if (entry.isDirectory() || !safePath(name)) unsafe.add(name == null ? "<missing>" : name);
                byte[] content = boundedRead(zip, MAX_ENTRY_BYTES);
                total += content.length;
                if (total > MAX_TOTAL_EXPANDED_BYTES) throw new IOException("Run-record archive expands beyond the safe limit");
                if (safePath(name) && !entry.isDirectory() && files.putIfAbsent(name, content) != null)
                    unsafe.add(name + " (duplicate)");
            }
        } catch (java.util.zip.ZipException invalid) {
            throw new IOException("Run-record archive is not a valid ZIP", invalid);
        }
        byte[] recordBytes = files.get("record.json");
        if (recordBytes == null) throw new IOException("Run-record archive has no record.json");
        JsonNode record;
        try { record = JSON.readTree(recordBytes); }
        catch (Exception invalid) { throw new IOException("Run-record archive record.json is invalid", invalid); }
        if (record == null || !record.isObject() || !"forgeloop.run-record/1".equals(record.path("schema").asText()))
            throw new IOException("Run-record schema is unsupported");
        return new RunRecordArchive(record, files, unsafe);
    }

    public JsonNode record() { return record.deepCopy(); }
    public byte[] entry(String path) {
        byte[] value = entries.get(path);
        return value == null ? null : value.clone();
    }

    public Map<String, byte[]> entries() {
        Map<String, byte[]> copy = new HashMap<>();
        entries.forEach((path, bytes) -> copy.put(path, bytes.clone()));
        return Map.copyOf(copy);
    }

    /** Checks both ZIP safety and the record's independent content-addressed file manifest. */
    public void verifyIntegrity(VerifyReport report) {
        boolean valid = unsafeEntries.isEmpty();
        String detail = unsafeEntries.isEmpty() ? "Archive paths are safe and unique" : "Archive contains an unsafe or duplicate entry path";
        for (String ignored : unsafeEntries) valid = false;

        byte[] recordBytes = entries.get("record.json");
        byte[] sidecar = entries.get("record.json.sha256");
        String recordHash = sha256(recordBytes);
        if (sidecar == null || !recordHash.equals(new String(sidecar, StandardCharsets.US_ASCII).strip())) {
            valid = false;
            detail = "record.json does not match its SHA-256 sidecar";
        }

        JsonNode filesNode = record.path("files");
        Set<String> listed = new HashSet<>();
        if (!filesNode.isArray()) valid = false;
        else for (JsonNode file : filesNode) {
            String path = file.path("path").asText("");
            if (!safePath(path) || !listed.add(path)) {
                valid = false;
                continue;
            }
            byte[] bytes = entries.get(path);
            if (bytes == null || !file.path("sizeBytes").canConvertToLong()
                    || file.path("sizeBytes").asLong(-1) != bytes.length
                    || !sha256(bytes).equals(file.path("sha256").asText(""))) valid = false;
        }
        for (String path : entries.keySet()) {
            if (!Set.of("record.json", "record.json.sha256").contains(path) && !listed.contains(path)) valid = false;
        }

        Map<String, JsonNode> listedByPath = new HashMap<>();
        if (filesNode.isArray()) filesNode.forEach(file -> listedByPath.put(file.path("path").asText(""), file));
        Set<String> artifactIds = new HashSet<>();
        JsonNode artifactsNode = record.path("artifacts");
        if (artifactsNode.isArray()) for (JsonNode artifact : artifactsNode) {
            String id = artifact.path("id").asText("");
            String path = artifact.path("archivePath").asText("");
            JsonNode file = listedByPath.get(path);
            if (id.isBlank() || !artifactIds.add(id) || file == null
                    || !id.equals(file.path("artifactId").asText())
                    || !artifact.path("sha256").asText().equals(file.path("sha256").asText())
                    || artifact.path("sizeBytes").asLong(-1) != file.path("sizeBytes").asLong(-2)) valid = false;
        }
        if (artifactsNode.isArray()) for (JsonNode file : filesNode) {
            String id = file.path("artifactId").asText("");
            if (!id.isBlank() && !artifactIds.contains(id)) valid = false;
        }
        report.add("archive", "record.json and files[]", valid ? VerifyVerdict.PASS : VerifyVerdict.FAIL,
                valid ? detail : "Archive contents, paths, size, digest, or artifact index do not match the run record");
    }

    public static boolean safePath(String value) {
        if (value == null || value.isBlank() || value.startsWith("/") || value.startsWith("\\")
                || value.contains("\\") || value.matches("^[A-Za-z]:.*") || value.indexOf('\u0000') >= 0) return false;
        for (String part : value.split("/", -1)) if (part.isBlank() || part.equals(".") || part.equals("..")) return false;
        return true;
    }

    static byte[] boundedRead(InputStream input, long maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        for (int count; (count = input.read(buffer)) != -1; ) {
            total += count;
            if (total > maxBytes) throw new IOException("Archive entry exceeds the safe size limit");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception unavailable) { throw new IllegalStateException("SHA-256 is unavailable", unavailable); }
    }
}
