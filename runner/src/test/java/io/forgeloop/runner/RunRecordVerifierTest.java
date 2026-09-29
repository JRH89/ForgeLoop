package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunRecordVerifierTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final RunRecordVerifier verifier = new RunRecordVerifier();

    @Test
    void verifiesManifestAndSidecarWithoutProviderOrNetwork(@TempDir Path temp) throws Exception {
        Path repo = repository(temp);
        Path archive = temp.resolve("record.zip");
        byte[] content = "immutable evidence".getBytes(StandardCharsets.UTF_8);
        writeArchive(archive, baseRecord(null, null, null), List.of());

        VerifyReport report = verifier.verifyRun(archive, repo);

        assertEquals(0, report.exitCode());
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("archive") && check.verdict() == VerifyVerdict.PASS));
        assertFalse(new String(content, StandardCharsets.UTF_8).isBlank());
    }

    @Test
    void detectsChangedArchivedArtifactBytes(@TempDir Path temp) throws Exception {
        Path repo = repository(temp);
        Path archive = temp.resolve("tampered.zip");
        byte[] expected = "evidence-A".getBytes(StandardCharsets.UTF_8);
        byte[] changed = "evidence-B".getBytes(StandardCharsets.UTF_8);
        Artifact artifact = new Artifact("artifact-1", "task-1", "lease-1", "VERIFICATION_BUNDLE",
                "evidence.json", expected, changed, false);
        writeArchive(archive, baseRecord(artifact, task(artifact), List.of()), List.of(artifact));

        VerifyReport report = verifier.verifyRun(archive, repo);

        assertEquals(1, report.exitCode());
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("archive") && check.verdict() == VerifyVerdict.FAIL));
    }

    @Test
    void verifiesBundleAndRowDigestsWithTheirDifferentArtifactReferences(@TempDir Path temp) throws Exception {
        Path repo = repository(temp);
        Path archive = temp.resolve("evidence.zip");
        String artifactReference = "artifact://org-1/run-1/task-1/lease-1/evidence.json";
        byte[] bundleBytes = verificationBundle("lease-1", artifactReference);
        Artifact bundleArtifact = new Artifact("bundle-1", "task-1", "lease-1", "VERIFICATION_BUNDLE",
                "evidence.json", bundleBytes, bundleBytes, false);
        ObjectNode task = task(bundleArtifact);
        ObjectNode record = baseRecord(bundleArtifact, task, List.of());
        record.putArray("verificationEvidence").add(verificationRow(bundleArtifact, artifactReference));
        writeArchive(archive, record, List.of(bundleArtifact));

        VerifyReport report = verifier.verifyRun(archive, repo);

        assertEquals(0, report.exitCode(), report.toJson());
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("evidence-bundles") && check.verdict() == VerifyVerdict.PASS));
    }

    @Test
    void rejectsVerificationBundleLinkedToAnotherLease(@TempDir Path temp) throws Exception {
        Path repo = repository(temp);
        Path archive = temp.resolve("wrong-evidence-link.zip");
        String artifactReference = "artifact://org-1/run-1/task-1/lease-1/evidence.json";
        byte[] bundleBytes = verificationBundle("lease-1", artifactReference);
        Artifact bundleArtifact = new Artifact("bundle-1", "task-1", "lease-1", "VERIFICATION_BUNDLE",
                "evidence.json", bundleBytes, bundleBytes, false);
        ObjectNode task = task(bundleArtifact);
        ObjectNode record = baseRecord(bundleArtifact, task, List.of());
        ObjectNode row = verificationRow(bundleArtifact, artifactReference);
        row.put("leaseId", "lease-other");
        record.putArray("verificationEvidence").add(row);
        writeArchive(archive, record, List.of(bundleArtifact));

        VerifyReport report = verifier.verifyRun(archive, repo);

        assertEquals(1, report.exitCode());
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("evidence-bundles") && check.verdict() == VerifyVerdict.FAIL));
    }

    @Test
    void rejectsUnsafeZipPaths(@TempDir Path temp) throws Exception {
        Path repo = repository(temp);
        Path archive = temp.resolve("unsafe.zip");
        writeArchive(archive, baseRecord(null, null, null), List.of());
        appendUnsafeEntry(archive, temp.resolve("unsafe-with-entry.zip"));

        VerifyReport report = verifier.verifyRun(temp.resolve("unsafe-with-entry.zip"), repo);

        assertEquals(1, report.exitCode());
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("archive") && check.verdict() == VerifyVerdict.FAIL));
    }

    @Test
    void acceptsIdenticalJournalOverlapsAndVerifiesTheChain(@TempDir Path temp) throws Exception {
        Path repo = repository(temp);
        Path archive = temp.resolve("journal.zip");
        byte[] journal = journal("WORKER_STARTED", "WORKER_ENDED");
        Artifact first = new Artifact("journal-a", "task-1", "lease-1", "RUN_JOURNAL",
                "journal-1-2.jsonl.gz", gzip(journal), gzip(journal), true);
        Artifact overlap = new Artifact("journal-b", "task-1", "lease-1", "RUN_JOURNAL",
                "journal-1-2.jsonl.gz", gzip(journal), gzip(journal), true);
        ObjectNode task = task(first);
        ArrayNode journalRefs = task.putArray("journal");
        journalRefs.add(journalRef(first, 1, 2));
        journalRefs.add(journalRef(overlap, 1, 2));
        writeArchive(archive, baseRecord(first, task, List.of(overlap)), List.of(first, overlap));

        VerifyReport report = verifier.verifyRun(archive, repo);

        assertEquals(0, report.exitCode(), report.toJson());
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("journal-chain") && check.verdict() == VerifyVerdict.PASS));
    }

    @Test
    void rejectsJournalSegmentsReferencedOutOfSequence(@TempDir Path temp) throws Exception {
        Path repo = repository(temp);
        byte[] full = journal("WORKER_STARTED", "WORKER_ENDED");
        String[] lines = new String(full, StandardCharsets.UTF_8).split("\\n");
        byte[] secondLine = (lines[1] + "\n").getBytes(StandardCharsets.UTF_8);
        byte[] firstLine = (lines[0] + "\n").getBytes(StandardCharsets.UTF_8);
        Artifact later = new Artifact("journal-later", "task-1", "lease-1", "RUN_JOURNAL",
                "journal-2-2.jsonl.gz", gzip(secondLine), gzip(secondLine), true);
        Artifact earlier = new Artifact("journal-earlier", "task-1", "lease-1", "RUN_JOURNAL",
                "journal-1-1.jsonl.gz", gzip(firstLine), gzip(firstLine), true);
        ObjectNode task = task(later);
        task.putArray("journal").add(journalRef(later, 2, 2)).add(journalRef(earlier, 1, 1));
        Path archive = temp.resolve("out-of-order.zip");
        writeArchive(archive, baseRecord(later, task, List.of(earlier)), List.of(later, earlier));

        VerifyReport report = verifier.verifyRun(archive, repo);

        assertEquals(1, report.exitCode(), report.toJson());
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("journal-segment-order")
                && check.verdict() == VerifyVerdict.FAIL));
    }

    @Test
    void reportsConflictingOverlapAsFailureAndMissingSequenceAsUnverifiable(@TempDir Path temp) throws Exception {
        Path repo = repository(temp);
        byte[] firstBytes = journal("WORKER_STARTED", "WORKER_ENDED");
        byte[] conflictingBytes = journal("WORKER_STARTED", "OTHER_EVENT");
        Artifact first = new Artifact("journal-a", "task-1", "lease-1", "RUN_JOURNAL", "journal-1-2.jsonl.gz",
                gzip(firstBytes), gzip(firstBytes), true);
        Artifact conflicting = new Artifact("journal-b", "task-1", "lease-1", "RUN_JOURNAL", "journal-1-2.jsonl.gz",
                gzip(conflictingBytes), gzip(conflictingBytes), true);
        ObjectNode conflictTask = task(first);
        conflictTask.putArray("journal").add(journalRef(first, 1, 2)).add(journalRef(conflicting, 1, 2));
        Path conflictArchive = temp.resolve("conflict.zip");
        writeArchive(conflictArchive, baseRecord(first, conflictTask, List.of(conflicting)), List.of(first, conflicting));
        VerifyReport conflictReport = verifier.verifyRun(conflictArchive, repo);
        assertEquals(1, conflictReport.exitCode());
        assertTrue(conflictReport.checks().stream().anyMatch(check -> check.id().equals("journal-overlap") && check.verdict() == VerifyVerdict.FAIL));

        Path gapArchive = temp.resolve("gap.zip");
        byte[] gapJournal = journalWithGap();
        Artifact gap = new Artifact("journal-gap", "task-1", "lease-1", "RUN_JOURNAL", "journal-1-3.jsonl.gz",
                gzip(gapJournal), gzip(gapJournal), true);
        ObjectNode gapTask = task(gap);
        gapTask.putArray("journal").add(journalRef(gap, 1, 3));
        writeArchive(gapArchive, baseRecord(gap, gapTask, List.of()), List.of(gap));
        VerifyReport gapReport = verifier.verifyRun(gapArchive, repo);
        assertEquals(3, gapReport.exitCode());
        assertTrue(gapReport.checks().stream().anyMatch(check -> check.id().equals("journal-chain") && check.verdict() == VerifyVerdict.UNVERIFIABLE));
    }

    @Test
    void redactedJournalLinksNeverPass(@TempDir Path temp) throws Exception {
        Path repo = repository(temp);
        Path archive = temp.resolve("redacted.zip");
        byte[] compressed = gzip(("{\"redacted\":true,\"seq\":1,\"leaseId\":\"lease-1\",\"type\":\"WORKER_STARTED\",\"sha256\":\"" + "a".repeat(64) + "\",\"line\":\"{}\"}\n").getBytes(StandardCharsets.UTF_8));
        Artifact artifact = new Artifact("journal-redacted", "task-1", "lease-1", "RUN_JOURNAL", "journal-1-1.jsonl.gz", compressed, compressed, true);
        ObjectNode task = task(artifact);
        task.putArray("journal").add(journalRef(artifact, 1, 1));
        writeArchive(archive, baseRecord(artifact, task, List.of()), List.of(artifact));

        VerifyReport report = verifier.verifyRun(archive, repo);

        assertEquals(3, report.exitCode());
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("journal-chain") && check.verdict() == VerifyVerdict.UNVERIFIABLE));
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("tool-call-order")
                && check.verdict() == VerifyVerdict.UNVERIFIABLE));
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("denied-tool-no-write")
                && check.verdict() == VerifyVerdict.UNVERIFIABLE));
    }

    @Test
    void verifiesRunnerLocalJournalAndTreatsTornTailAsUnverifiable(@TempDir Path temp) throws Exception {
        Path repo = repository(temp);
        byte[] completeJournal = journal("WORKER_STARTED", "WORKER_ENDED");
        Path complete = temp.resolve("local.jsonl");
        Files.write(complete, completeJournal);

        VerifyReport completeReport = verifier.verifyJournal(complete, repo);

        assertEquals(0, completeReport.exitCode(), completeReport.toJson());
        Path torn = temp.resolve("torn.jsonl");
        Files.write(torn, (new String(completeJournal, StandardCharsets.UTF_8) + "{partial").getBytes(StandardCharsets.UTF_8));
        VerifyReport tornReport = verifier.verifyJournal(torn, repo);
        assertEquals(3, tornReport.exitCode());
        assertTrue(tornReport.checks().stream().anyMatch(check -> check.id().equals("journal-chain")
                && check.verdict() == VerifyVerdict.UNVERIFIABLE));
    }

    @Test
    void recountsConversationAppendBytesAgainstLoopCounters(@TempDir Path temp) throws Exception {
        Path repo = repository(temp);
        Path valid = temp.resolve("loop-valid.jsonl");
        byte[] serializedItem = "{\"text\":\"hello\"}".getBytes(StandardCharsets.UTF_8);
        Files.write(valid, loopJournal(serializedItem, serializedItem.length));

        VerifyReport validReport = verifier.verifyJournal(valid, repo);
        assertEquals(0, validReport.exitCode(), validReport.toJson());
        assertTrue(validReport.checks().stream().anyMatch(check -> check.id().equals("loop-conversation-bytes")
                && check.verdict() == VerifyVerdict.PASS));

        Path invalid = temp.resolve("loop-invalid.jsonl");
        Files.write(invalid, loopJournal(serializedItem, serializedItem.length + 1));
        VerifyReport invalidReport = verifier.verifyJournal(invalid, repo);
        assertEquals(1, invalidReport.exitCode());
        assertTrue(invalidReport.checks().stream().anyMatch(check -> check.id().equals("loop-conversation-bytes")
                && check.verdict() == VerifyVerdict.FAIL));
    }

    @Test
    void rebuildsConversationRequestParsesResponseAndLinksUsageToItsLease(@TempDir Path temp) throws Exception {
        Path repo = repository(temp);
        Path archive = temp.resolve("provider-record.zip");
        writeConversationArchive(archive, 3);

        VerifyReport report = verifier.verifyRun(archive, repo);

        assertEquals(0, report.exitCode(), report.toJson());
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("provider-request")
                && check.verdict() == VerifyVerdict.PASS));
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("provider-response")
                && check.verdict() == VerifyVerdict.PASS));
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("attempt-row-link")
                && check.verdict() == VerifyVerdict.PASS));
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("answered-model")
                && check.verdict() == VerifyVerdict.PASS));
    }

    @Test
    void rejectsProviderUsageRowThatDiffersFromTheParsedResponse(@TempDir Path temp) throws Exception {
        Path repo = repository(temp);
        Path archive = temp.resolve("provider-row-mismatch.zip");
        writeConversationArchive(archive, 4);

        VerifyReport report = verifier.verifyRun(archive, repo);

        assertEquals(1, report.exitCode());
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("attempt-row-link")
                && check.verdict() == VerifyVerdict.FAIL));
    }

    @Test
    void treatsPreLeaseProviderRowsAsUnverifiableAndRejectsUnmatchedRows(@TempDir Path temp) throws Exception {
        Path repo = repository(temp);
        Path legacy = temp.resolve("legacy-row.zip");
        writeConversationArchive(legacy, 3, true, false);

        VerifyReport legacyReport = verifier.verifyRun(legacy, repo);

        assertEquals(3, legacyReport.exitCode(), legacyReport.toJson());
        assertTrue(legacyReport.checks().stream().anyMatch(check -> check.id().equals("attempt-row-link")
                && check.verdict() == VerifyVerdict.UNVERIFIABLE));

        Path extra = temp.resolve("extra-row.zip");
        writeConversationArchive(extra, 3, false, true);
        VerifyReport extraReport = verifier.verifyRun(extra, repo);
        assertEquals(1, extraReport.exitCode(), extraReport.toJson());
        assertTrue(extraReport.checks().stream().anyMatch(check -> check.id().equals("attempt-row-link")
                && check.verdict() == VerifyVerdict.FAIL));
    }

    @Test
    void linksFailedConversationAttemptsUsingTheirRecordedCorrelationDigest(@TempDir Path temp) throws Exception {
        Path repo = repository(temp);
        Path archive = temp.resolve("failed-turn.zip");
        byte[] journal = failedConversationJournal();
        byte[] compressed = gzip(journal);
        Artifact journalArtifact = new Artifact("journal-failed", "task-1", "lease-1", "RUN_JOURNAL",
                "journal-1-5.jsonl.gz", compressed, compressed, true);
        ObjectNode task = task(journalArtifact);
        task.putArray("journal").add(journalRef(journalArtifact, 1, 5));
        ObjectNode attempt = task.putArray("providerAttempts").addObject();
        attempt.put("id", "usage-failed"); attempt.put("leaseId", "lease-1");
        attempt.put("provider", "local"); attempt.put("model", "gpt-test");
        attempt.put("requestIdDigest", sha(String.join("\0", "lease-1/turn-1", "local", "gpt-test", "2",
                "TRANSIENT_PROVIDER_FAILURE").getBytes(StandardCharsets.UTF_8)));
        attempt.put("inputTokens", 0); attempt.put("outputTokens", 0); attempt.put("attemptCount", 2);
        attempt.put("estimatedCostMicros", 0); attempt.put("costKnown", false); attempt.put("outcome", "FAILED");
        attempt.put("retryable", true); attempt.put("category", "TRANSIENT_PROVIDER_FAILURE");
        writeArchive(archive, baseRecord(journalArtifact, task, List.of()), List.of(journalArtifact));

        VerifyReport report = verifier.verifyRun(archive, repo);

        assertEquals(0, report.exitCode(), report.toJson());
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("attempt-row-link")
                && check.verdict() == VerifyVerdict.PASS));
    }

    @Test
    void verifiesOutputLimitStopsAgainstTheRecordedTruncationRetry(@TempDir Path temp) throws Exception {
        Path repo = repository(temp);
        Path journal = temp.resolve("output-limit.jsonl");
        Files.write(journal, outputLimitJournal(true));

        VerifyReport report = verifier.verifyJournal(journal, repo);

        assertEquals(0, report.exitCode(), report.toJson());
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("budget-stop")
                && check.verdict() == VerifyVerdict.PASS));
    }

    private static Path repository(Path temp) throws Exception {
        Path repo = Files.createDirectory(temp.resolve("checkout"));
        Files.createDirectory(repo.resolve(".git"));
        return repo;
    }

    private static ObjectNode baseRecord(Artifact first, ObjectNode firstTask, List<Artifact> additional) throws Exception {
        ObjectNode record = JSON.createObjectNode();
        record.put("schema", "forgeloop.run-record/1");
        record.putObject("run").put("id", "run-1").put("runRecord", true);
        ArrayNode tasks = record.putArray("tasks");
        if (firstTask != null) tasks.add(firstTask);
        ArrayNode evidence = record.putArray("verificationEvidence");
        ArrayNode artifacts = record.putArray("artifacts");
        ArrayNode files = record.putArray("files");
        List<Artifact> all = new java.util.ArrayList<>();
        if (first != null) all.add(first);
        all.addAll(additional == null ? List.of() : additional);
        for (Artifact item : all) {
            String archivePath = "artifacts/" + item.id() + "/" + item.name();
            ObjectNode metadata = artifacts.addObject();
            metadata.put("id", item.id()); metadata.put("taskId", item.taskId()); metadata.put("leaseId", item.leaseId());
            metadata.put("artifactType", item.type()); metadata.put("displayName", item.name());
            metadata.put("archivePath", archivePath); metadata.put("sizeBytes", item.expected().length);
            metadata.put("sha256", sha(item.expected()));
            ObjectNode file = files.addObject(); file.put("path", archivePath); file.put("artifactId", item.id());
            file.put("taskId", item.taskId()); file.put("leaseId", item.leaseId()); file.put("artifactType", item.type());
            file.put("sizeBytes", item.expected().length); file.put("sha256", sha(item.expected()));
        }
        return record;
    }

    private static ObjectNode task(Artifact artifact) {
        ObjectNode task = JSON.createObjectNode(); task.put("id", artifact.taskId()); task.put("planKey", "worker");
        ArrayNode attempts = task.putArray("attempts");
        attempts.addObject().put("id", artifact.leaseId()).put("taskId", artifact.taskId());
        task.putArray("providerAttempts"); task.putArray("verificationEvidence"); task.putArray("testCheckEvidence"); task.putArray("reviewEvidence");
        if (artifact.journal()) task.putArray("journal").add(journalRef(artifact, 1, 2));
        else task.putArray("journal");
        return task;
    }

    private static ObjectNode verificationRow(Artifact artifact, String artifactReference) throws Exception {
        ObjectNode bundle = (ObjectNode) JSON.readTree(artifact.expected());
        ObjectNode row = JSON.createObjectNode();
        row.put("id", "evidence-row"); row.put("taskId", artifact.taskId()); row.put("leaseId", artifact.leaseId());
        row.put("bundleArtifactId", artifact.id()); row.put("artifactReference", artifactReference);
        row.put("kind", bundle.path("kind").asText()); row.putNull("gate"); row.put("image", "sha256:test");
        row.set("command", bundle.path("command").deepCopy()); row.put("exitCode", 0); row.put("timedOut", false);
        row.put("output", bundle.path("output").asText()); row.put("outputDigest", bundle.path("outputDigest").asText());
        row.put("bundleDigest", bundleDigest(bundle, artifactReference));
        row.put("startedAt", bundle.path("startedAt").asText()); row.put("finishedAt", bundle.path("finishedAt").asText());
        return row;
    }

    private static byte[] verificationBundle(String leaseId, String artifactReference) throws Exception {
        ObjectNode bundle = JSON.createObjectNode();
        bundle.put("kind", "CONTAINER"); bundle.putNull("gate"); bundle.put("image", "sha256:test");
        bundle.set("command", JSON.valueToTree(List.of("mvn", "test")));
        bundle.put("exitCode", 0); bundle.put("timedOut", false);
        bundle.put("startedAt", "2026-09-29T12:00:00Z"); bundle.put("finishedAt", "2026-09-29T12:00:01Z");
        bundle.putNull("artifactReference");
        String output = "tests passed";
        String outputDigest = sha(output.getBytes(StandardCharsets.UTF_8));
        bundle.put("outputDigest", outputDigest); bundle.put("output", output);
        bundle.put("bundleDigest", bundleDigest(bundle, ""));
        return JSON.writeValueAsBytes(bundle);
    }

    private static String bundleDigest(ObjectNode bundle, String artifactReference) {
        String material = String.join("\u0000", bundle.path("kind").asText(), "", bundle.path("image").asText(),
                String.join("\u001f", List.of("mvn", "test")), Integer.toString(bundle.path("exitCode").asInt()),
                Boolean.toString(bundle.path("timedOut").asBoolean()), bundle.path("outputDigest").asText(),
                bundle.path("startedAt").asText(), bundle.path("finishedAt").asText(), artifactReference);
        return sha(material.getBytes(StandardCharsets.UTF_8));
    }

    private static ObjectNode journalRef(Artifact artifact, long first, long last) {
        ObjectNode value = JSON.createObjectNode(); value.put("artifactId", artifact.id()); value.put("displayName", artifact.name());
        value.put("leaseId", artifact.leaseId()); value.put("sha256", sha(artifact.expected()));
        value.put("sizeBytes", artifact.expected().length); value.put("firstSeq", first); value.put("lastSeq", last); return value;
    }

    private static void writeArchive(Path target, ObjectNode record, List<Artifact> artifacts) throws Exception {
        byte[] recordBytes = JSON.writeValueAsBytes(record);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target), StandardCharsets.UTF_8)) {
            entry(zip, "record.json", recordBytes);
            entry(zip, "record.json.sha256", (sha(recordBytes) + "\n").getBytes(StandardCharsets.US_ASCII));
            for (Artifact artifact : artifacts) entry(zip, "artifacts/" + artifact.id() + "/" + artifact.name(), artifact.actual());
        }
    }

    private static void appendUnsafeEntry(Path source, Path target) throws Exception {
        Map<String, byte[]> entries = new java.util.HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(source))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) entries.put(entry.getName(), zip.readAllBytes());
        }
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            for (var entry : entries.entrySet()) entry(zip, entry.getKey(), entry.getValue());
            entry(zip, "../escape.txt", new byte[]{1});
        }
    }

    private static void entry(ZipOutputStream zip, String name, byte[] content) throws Exception {
        zip.putNextEntry(new ZipEntry(name)); zip.write(content); zip.closeEntry();
    }

    private static byte[] journal(String firstType, String secondType) throws Exception {
        String first = JSON.writeValueAsString(Map.of("seq", 1, "leaseId", "lease-1", "type", firstType,
                "prev", "0".repeat(64), "taskId", "task-1"));
        String second = JSON.writeValueAsString(Map.of("seq", 2, "leaseId", "lease-1", "type", secondType,
                "prev", sha(first.getBytes(StandardCharsets.UTF_8)), "taskId", "task-1"));
        return (first + "\n" + second + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] journalWithGap() throws Exception {
        String first = JSON.writeValueAsString(Map.of("seq", 1, "leaseId", "lease-1", "type", "WORKER_STARTED",
                "prev", "0".repeat(64), "taskId", "task-1"));
        String third = JSON.writeValueAsString(Map.of("seq", 3, "leaseId", "lease-1", "type", "WORKER_ENDED",
                "prev", sha(first.getBytes(StandardCharsets.UTF_8)), "taskId", "task-1"));
        return (first + "\n" + third + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static void writeConversationArchive(Path archive, long rowInputTokens) throws Exception {
        writeConversationArchive(archive, rowInputTokens, false, false);
    }

    private static void writeConversationArchive(Path archive, long rowInputTokens, boolean legacyLease, boolean extraRow) throws Exception {
        ConversationRequest request = new ConversationRequest("gpt-test", "Be concise.", List.of(new UserText("hello")),
                List.of(), 128, Duration.ofSeconds(30));
        ProviderClientFactory codecs = new ProviderClientFactory();
        String response = "{\"id\":\"req-1\",\"model\":\"gpt-test\",\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2},"
                + "\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}";
        ObjectNode started = journalEvent(1, "LOOP_STARTED", "0".repeat(64));
        started.put("taskId", "task-1"); started.put("provider", "local"); started.put("model", "gpt-test");
        byte[] first = JSON.writeValueAsBytes(started);
        ObjectNode requested = journalEvent(2, "TURN_REQUESTED", sha(first));
        requested.put("turn", 1); requested.put("correlationId", "lease-1/turn-1");
        requested.put("requestSha256", sha(codecs.conversationBody("local", request).getBytes(StandardCharsets.UTF_8)));
        requested.set("conversationRequest", JSON.valueToTree(ConversationRequestJournalCodec.encode(request)));
        byte[] second = JSON.writeValueAsBytes(requested);
        ObjectNode completed = journalEvent(3, "TURN_COMPLETED", sha(second));
        completed.put("turn", 1); completed.put("correlationId", "lease-1/turn-1"); completed.put("attemptCount", 1);
        completed.put("stopReason", "END_TURN"); completed.put("inputTokens", 3); completed.put("outputTokens", 2);
        completed.put("providerRequestId", "req-1"); completed.put("answeredModel", "gpt-test"); completed.put("text", "ok");
        completed.set("toolCalls", JSON.createArrayNode()); completed.put("responseBody", response);
        byte[] third = JSON.writeValueAsBytes(completed);
        byte[] journal = (new String(first, StandardCharsets.UTF_8) + "\n"
                + new String(second, StandardCharsets.UTF_8) + "\n"
                + new String(third, StandardCharsets.UTF_8) + "\n").getBytes(StandardCharsets.UTF_8);
        byte[] compressed = gzip(journal);
        Artifact journalArtifact = new Artifact("journal-provider", "task-1", "lease-1", "RUN_JOURNAL",
                "journal-1-3.jsonl.gz", compressed, compressed, true);
        ObjectNode task = task(journalArtifact);
        task.putArray("journal").add(journalRef(journalArtifact, 1, 3));
        ObjectNode attempt = task.putArray("providerAttempts").addObject();
        if (legacyLease) attempt.putNull("leaseId"); else attempt.put("leaseId", "lease-1");
        attempt.put("provider", "local"); attempt.put("model", "gpt-test");
        attempt.put("answeredModel", "gpt-test"); attempt.put("inputTokens", rowInputTokens); attempt.put("outputTokens", 2);
        attempt.put("attemptCount", 1); attempt.put("requestIdDigest", sha("req-1".getBytes(StandardCharsets.UTF_8)));
        attempt.put("outcome", "SUCCEEDED");
        if (extraRow) {
            ObjectNode extra = task.withArray("providerAttempts").addObject();
            extra.put("id", "usage-extra"); extra.put("leaseId", "lease-1"); extra.put("provider", "local");
            extra.put("model", "gpt-test"); extra.put("answeredModel", "gpt-test"); extra.put("inputTokens", 3);
            extra.put("outputTokens", 2); extra.put("attemptCount", 1); extra.put("requestIdDigest", "f".repeat(64));
            extra.put("outcome", "SUCCEEDED");
        }
        ObjectNode record = baseRecord(journalArtifact, task, List.of());
        writeArchive(archive, record, List.of(journalArtifact));
    }

    private static byte[] loopJournal(byte[] serializedItem, long terminalBytes) throws Exception {
        ObjectNode added = journalEvent(1, "CONVERSATION_ITEM_ADDED", "0".repeat(64));
        added.put("itemBytes", serializedItem.length); added.put("itemSha256", sha(serializedItem));
        added.put("serializedItem", new String(serializedItem, StandardCharsets.UTF_8));
        byte[] firstLine = JSON.writeValueAsBytes(added);
        ObjectNode ended = journalEvent(2, "LOOP_ENDED", sha(firstLine));
        ObjectNode counters = ended.putObject("counters"); counters.put("turns", 0); counters.put("toolCalls", 0);
        counters.put("inputTokens", 0); counters.put("outputTokens", 0); counters.put("conversationBytes", terminalBytes);
        return (new String(firstLine, StandardCharsets.UTF_8) + "\n" + JSON.writeValueAsString(ended) + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] failedConversationJournal() throws Exception {
        ConversationRequest request = new ConversationRequest("gpt-test", "Be concise.", List.of(new UserText("hello")),
                List.of(), 128, Duration.ofSeconds(30));
        ProviderClientFactory codecs = new ProviderClientFactory();
        byte[] item = JSON.writeValueAsBytes(new UserText("hello"));
        ObjectNode started = journalEvent(1, "LOOP_STARTED", "0".repeat(64));
        started.put("taskId", "task-1"); started.put("provider", "local"); started.put("model", "gpt-test");
        byte[] first = JSON.writeValueAsBytes(started);
        ObjectNode added = journalEvent(2, "CONVERSATION_ITEM_ADDED", sha(first));
        added.put("itemBytes", item.length); added.put("itemSha256", sha(item));
        added.put("serializedItem", new String(item, StandardCharsets.UTF_8));
        byte[] second = JSON.writeValueAsBytes(added);
        ObjectNode requested = journalEvent(3, "TURN_REQUESTED", sha(second));
        requested.put("turn", 1); requested.put("correlationId", "lease-1/turn-1");
        requested.put("requestSha256", sha(codecs.conversationBody("local", request).getBytes(StandardCharsets.UTF_8)));
        requested.set("conversationRequest", JSON.valueToTree(ConversationRequestJournalCodec.encode(request)));
        byte[] third = JSON.writeValueAsBytes(requested);
        ObjectNode failed = journalEvent(4, "TURN_FAILED", sha(third));
        failed.put("turn", 1); failed.put("correlationId", "lease-1/turn-1"); failed.put("attemptCount", 2);
        failed.put("retryable", true); failed.put("category", "TRANSIENT_PROVIDER_FAILURE");
        byte[] fourth = JSON.writeValueAsBytes(failed);
        ObjectNode ended = journalEvent(5, "LOOP_ENDED", sha(fourth));
        ended.put("outcome", "PROVIDER_FAILURE"); ended.put("budgetKind", "");
        ObjectNode counters = ended.putObject("counters"); counters.put("turns", 1); counters.put("toolCalls", 0);
        counters.put("inputTokens", 0); counters.put("outputTokens", 0); counters.put("conversationBytes", item.length);
        counters.put("knownCostMicros", 0);
        return (new String(first, StandardCharsets.UTF_8) + "\n" + new String(second, StandardCharsets.UTF_8) + "\n"
                + new String(third, StandardCharsets.UTF_8) + "\n" + new String(fourth, StandardCharsets.UTF_8) + "\n"
                + JSON.writeValueAsString(ended) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] outputLimitJournal(boolean retry) throws Exception {
        ConversationRequest request = new ConversationRequest("gpt-test", "Be concise.", List.of(new UserText("hello")),
                List.of(), 128, Duration.ofSeconds(30));
        ProviderClientFactory codecs = new ProviderClientFactory();
        byte[] item = JSON.writeValueAsBytes(new UserText("hello"));
        String response = "{\"id\":\"req-1\",\"model\":\"gpt-test\",\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2},"
                + "\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"role\":\"assistant\",\"content\":\"partial\"}}]}";
        ObjectNode started = journalEvent(1, "LOOP_STARTED", "0".repeat(64));
        started.put("taskId", "task-1"); started.put("provider", "local"); started.put("model", "gpt-test");
        byte[] first = JSON.writeValueAsBytes(started);
        ObjectNode added = journalEvent(2, "CONVERSATION_ITEM_ADDED", sha(first));
        added.put("itemBytes", item.length); added.put("itemSha256", sha(item));
        added.put("serializedItem", new String(item, StandardCharsets.UTF_8));
        byte[] second = JSON.writeValueAsBytes(added);
        ObjectNode requested = journalEvent(3, "TURN_REQUESTED", sha(second));
        requested.put("turn", 1); requested.put("correlationId", "lease-1/turn-1");
        requested.put("requestSha256", sha(codecs.conversationBody("local", request).getBytes(StandardCharsets.UTF_8)));
        requested.put("truncationRetry", retry);
        requested.set("conversationRequest", JSON.valueToTree(ConversationRequestJournalCodec.encode(request)));
        byte[] third = JSON.writeValueAsBytes(requested);
        ObjectNode completed = journalEvent(4, "TURN_COMPLETED", sha(third));
        completed.put("turn", 1); completed.put("correlationId", "lease-1/turn-1"); completed.put("attemptCount", 1);
        completed.put("stopReason", "MAX_TOKENS"); completed.put("inputTokens", 3); completed.put("outputTokens", 2);
        completed.put("providerRequestId", "req-1"); completed.put("answeredModel", "gpt-test"); completed.put("text", "partial");
        completed.set("toolCalls", JSON.createArrayNode()); completed.put("responseBody", response);
        byte[] fourth = JSON.writeValueAsBytes(completed);
        ObjectNode ended = journalEvent(5, "LOOP_ENDED", sha(fourth));
        ended.put("outcome", "BUDGET_STOP"); ended.put("budgetKind", "OUTPUT_LIMIT");
        ObjectNode counters = ended.putObject("counters"); counters.put("turns", 1); counters.put("toolCalls", 0);
        counters.put("inputTokens", 3); counters.put("outputTokens", 2); counters.put("conversationBytes", item.length);
        counters.put("knownCostMicros", 0);
        return (new String(first, StandardCharsets.UTF_8) + "\n" + new String(second, StandardCharsets.UTF_8) + "\n"
                + new String(third, StandardCharsets.UTF_8) + "\n" + new String(fourth, StandardCharsets.UTF_8) + "\n"
                + JSON.writeValueAsString(ended) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static ObjectNode journalEvent(long sequence, String type, String previous) {
        ObjectNode event = JSON.createObjectNode(); event.put("seq", sequence); event.put("at", "2026-09-29T12:00:00Z");
        event.put("leaseId", "lease-1"); event.put("type", type); event.put("prev", previous); return event;
    }

    private static byte[] gzip(byte[] bytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) { gzip.write(bytes); }
        return output.toByteArray();
    }

    private static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private record Artifact(String id, String taskId, String leaseId, String type, String name,
                            byte[] expected, byte[] actual, boolean journal) { }
}
