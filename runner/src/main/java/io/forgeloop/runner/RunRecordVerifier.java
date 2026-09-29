package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/** Performs the keyless, non-re-execution portion of Version 2 run-record verification. */
public final class RunRecordVerifier {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final byte[] FIRST_PREV = "0".repeat(64).getBytes(StandardCharsets.US_ASCII);
    private static final long MAX_JOURNAL_EXPANDED_BYTES = 32L * 1024 * 1024;

    public VerifyReport verifyRun(Path archivePath, Path repositoryPath) throws IOException {
        requireRepository(repositoryPath);
        RunRecordArchive archive = RunRecordArchive.open(archivePath);
        VerifyReport report = new VerifyReport();
        archive.verifyIntegrity(report);
        JsonNode record = archive.record();
        Map<String, JsonNode> artifacts = index(record.path("artifacts"), "id");
        List<TaskJournal> journals = readJournals(record, artifacts, archive, report);
        verifyRows(record, journals, report);
        verifyProviderCalls(record, journals, report);
        verifyConsistency(record, journals, report);
        verifyEvidence(record, artifacts, archive, report);
        return report;
    }

    /** Checks a runner-local journal without consulting the provider or control plane. */
    public VerifyReport verifyJournal(Path journalPath, Path repositoryPath) throws IOException {
        requireRepository(repositoryPath);
        if (journalPath == null || Files.isSymbolicLink(journalPath)
                || !Files.isRegularFile(journalPath, LinkOption.NOFOLLOW_LINKS)
                || Files.size(journalPath) > 256L * 1024 * 1024)
            throw new IOException("Run journal must be a regular, bounded, non-symlink file");
        byte[] bytes = Files.readAllBytes(journalPath);
        boolean tornTail = bytes.length > 0 && bytes[bytes.length - 1] != '\n';
        int complete = bytes.length;
        if (tornTail) {
            while (complete > 0 && bytes[complete - 1] != '\n') complete--;
        }
        List<JournalEvent> events = parseLines(java.util.Arrays.copyOf(bytes, complete), "local journal", null, false);
        VerifyReport report = new VerifyReport();
        ChainResult chain = verifyChain(events);
        report.add("journal-chain", journalPath.getFileName().toString(),
                chain.failure() ? VerifyVerdict.FAIL : chain.unverifiable() || tornTail ? VerifyVerdict.UNVERIFIABLE : VerifyVerdict.PASS,
                chain.failure() ? "Journal contains a broken chain or conflicting sequence" : tornTail
                        ? "The final incomplete line was excluded" : chain.unverifiable() ? "Journal has a gap or a redacted link" : "Journal hash chain is continuous");
        String taskId = events.stream().filter(event -> "WORKER_STARTED".equals(event.type()) && event.body() != null)
                .map(event -> event.body().path("taskId").asText("")).filter(value -> !value.isBlank()).findFirst()
                .orElse(journalPath.getFileName().toString());
        TaskJournal local = new TaskJournal(taskId, null, new TreeMap<>());
        events.forEach(event -> local.events().put(event.sequence(), event));
        verifyProviderCalls(null, List.of(local), report);
        verifyConsistency(null, List.of(local), report);
        return report;
    }

    private List<TaskJournal> readJournals(JsonNode record, Map<String, JsonNode> artifactById,
                                           RunRecordArchive archive, VerifyReport report) {
        List<TaskJournal> result = new ArrayList<>();
        JsonNode tasks = record.path("tasks");
        if (!tasks.isArray()) {
            report.add("journal-chain", "tasks", VerifyVerdict.FAIL, "Run record task index is invalid");
            return result;
        }
        boolean anySegments = false;
        for (JsonNode task : tasks) {
            String taskId = task.path("id").asText("");
            if (taskId.isBlank()) {
                report.add("journal-chain", "task", VerifyVerdict.FAIL, "Run record task identity is missing");
                continue;
            }
            TreeMap<Long, JournalEvent> merged = new TreeMap<>();
            JsonNode segments = task.path("journal");
            long previousFirstSequence = -1;
            boolean segmentOrderValid = true;
            if (segments.isArray()) for (JsonNode segment : segments) {
                anySegments = true;
                long referenceFirst = segment.path("firstSeq").asLong(-1);
                if (referenceFirst < 1 || previousFirstSequence > referenceFirst) segmentOrderValid = false;
                previousFirstSequence = referenceFirst;
                String artifactId = segment.path("artifactId").asText("");
                JsonNode metadata = artifactById.get(artifactId);
                if (metadata == null || !"RUN_JOURNAL".equals(metadata.path("artifactType").asText())
                        || !taskId.equals(metadata.path("taskId").asText())
                        || !segment.path("leaseId").asText().equals(metadata.path("leaseId").asText())) {
                    report.add("journal-chain", taskId, VerifyVerdict.FAIL, "Journal segment is not linked to this task attempt");
                    continue;
                }
                String path = metadata.path("archivePath").asText("");
                byte[] compressed = archive.entry(path);
                if (compressed == null) {
                    report.add("journal-chain", taskId, VerifyVerdict.FAIL, "Journal segment is missing from the archive");
                    continue;
                }
                try {
                    String displayName = metadata.path("displayName").asText("");
                    java.util.regex.Matcher filename = java.util.regex.Pattern.compile("journal-([1-9][0-9]{0,9})-([1-9][0-9]{0,9})\\.jsonl\\.gz")
                            .matcher(displayName);
                    if (!filename.matches() || !displayName.equals(segment.path("displayName").asText())
                            || !metadata.path("sha256").asText().equals(segment.path("sha256").asText())
                            || metadata.path("sizeBytes").asLong(-1) != segment.path("sizeBytes").asLong(-2)) {
                        report.add("journal-segment-metadata", artifactId, VerifyVerdict.FAIL,
                                "Journal reference metadata differs from its exported artifact index");
                        continue;
                    }
                    List<JournalEvent> events = parseLines(gunzip(compressed), taskId, metadata.path("leaseId").asText(), true);
                    long first = events.getFirst().sequence(), last = events.getLast().sequence();
                    if (first != referenceFirst || last != segment.path("lastSeq").asLong(-1)
                            || first != Long.parseLong(filename.group(1)) || last != Long.parseLong(filename.group(2)))
                        report.add("journal-segment-range", artifactId, VerifyVerdict.FAIL, "Segment filename range does not match its journal records");
                    for (JournalEvent event : events) {
                        JournalEvent old = merged.putIfAbsent(event.sequence(), event);
                        if (old != null && !old.rawLine().equals(event.rawLine()))
                            report.add("journal-overlap", taskId, VerifyVerdict.FAIL, "Overlapping segments contain different records");
                    }
                } catch (Exception invalid) {
                    report.add("journal-segment", artifactId, VerifyVerdict.FAIL, "Journal segment is malformed or exceeds its expansion limit");
                }
            }
            if (segments.isArray() && !segments.isEmpty()) report.add("journal-segment-order", taskId,
                    segmentOrderValid ? VerifyVerdict.PASS : VerifyVerdict.FAIL,
                    segmentOrderValid ? "Journal segment references are ordered by their first sequence"
                            : "Journal segments are not ordered by their first sequence");
            if (!merged.isEmpty()) {
                TaskJournal journal = new TaskJournal(taskId, task, merged);
                result.add(journal);
                ChainResult chain = verifyChain(new ArrayList<>(merged.values()));
                report.add("journal-chain", taskId, chain.failure() ? VerifyVerdict.FAIL
                                : chain.unverifiable() ? VerifyVerdict.UNVERIFIABLE : VerifyVerdict.PASS,
                        chain.failure() ? "Journal hash chain is broken" : chain.unverifiable()
                                ? "Journal has a gap or a redacted or omitted link" : "Journal links and identical overlaps are verified");
            } else if (task.path("providerAttempts").isArray() && !task.path("providerAttempts").isEmpty()) {
                report.add("journal-chain", taskId, VerifyVerdict.UNVERIFIABLE,
                        "Provider attempt rows exist but no uploaded journal segment is available for this task");
            }
        }
        if (!anySegments) {
            boolean hasAttempts = false;
            for (JsonNode task : tasks) hasAttempts |= task.path("providerAttempts").isArray() && !task.path("providerAttempts").isEmpty();
            report.add("journal-chain", "run", hasAttempts ? VerifyVerdict.UNVERIFIABLE : VerifyVerdict.PASS,
                    hasAttempts ? "Provider usage exists but no journal segments were exported" : "No recorded provider attempts require journal verification");
        }
        return List.copyOf(result);
    }

    private void verifyRows(JsonNode record, List<TaskJournal> journals, VerifyReport report) {
        boolean valid = true, unverifiable = false;
        Map<String, JsonNode> leases = new HashMap<>();
        for (TaskJournal journal : journals) {
            JsonNode attempts = journal.task().path("attempts");
            if (!attempts.isArray()) { valid = false; continue; }
            for (JsonNode attempt : attempts) {
                String id = attempt.path("id").asText("");
                if (!id.isBlank()) leases.put(id, attempt);
            }
            for (JournalEvent event : journal.events().values()) {
                if (event.body() == null) { unverifiable = true; continue; }
                JsonNode row = leases.get(event.leaseId());
                if (row == null) { valid = false; continue; }
                if (!journal.taskId().equals(row.path("taskId").asText())) valid = false;
                if (("WORKER_STARTED".equals(event.type()) || "LOOP_STARTED".equals(event.type()))
                        && !journal.taskId().equals(event.body().path("taskId").asText())) valid = false;
            }
        }
        report.add("journal-row-links", "task and lease attempts", !valid ? VerifyVerdict.FAIL
                        : unverifiable ? VerifyVerdict.UNVERIFIABLE : VerifyVerdict.PASS,
                !valid ? "A journal event references a missing or mismatched lease row"
                        : unverifiable ? "Redacted or omitted events cannot prove row links" : "Journal task and lease references match exported rows");
    }

    private void verifyProviderCalls(JsonNode record, List<TaskJournal> journals, VerifyReport report) {
        ProviderClientFactory codecs = new ProviderClientFactory();
        Set<String> matchedAttemptRows = new HashSet<>();
        int calls = 0;
        for (TaskJournal journal : journals) {
            Map<String, String> providerByLease = new HashMap<>();
            Map<String, String> modelByLease = new HashMap<>();
            Map<String, Boolean> sameBuildByLease = new HashMap<>();
            for (JournalEvent event : journal.events().values()) {
                if (event.body() == null) continue;
                JsonNode body = event.body();
                if ("WORKER_STARTED".equals(event.type()) || "LOOP_STARTED".equals(event.type())) {
                    String provider = body.path("provider").asText("");
                    String model = body.path("model").asText("");
                    providerByLease.put(event.leaseId(), provider);
                    modelByLease.put(event.leaseId(), model);
                }
                if ("ATTEMPT_PINS".equals(event.type())) {
                    String revision = body.path("runnerRevision").asText("");
                    sameBuildByLease.put(event.leaseId(), revision.matches("[0-9a-f]{7,64}")
                            && revision.equals(RunnerBuild.current().revision()));
                }
            }

            for (JournalEvent event : journal.events().values()) {
                if (event.body() == null) continue;
                JsonNode body = event.body();
                if ("CALL_REQUESTED".equals(event.type())) {
                    calls++;
                    String provider = providerByLease.getOrDefault(event.leaseId(), "");
                    boolean sameBuild = sameBuildByLease.getOrDefault(event.leaseId(), false);
                    try {
                        JsonNode schema = body.path("outputSchema");
                        ProviderRequest request = new ProviderRequest(body.path("model").asText(""),
                                body.path("instructions").asText(""), body.path("input").asText(""),
                                body.path("maxOutputTokens").asInt(-1), schema.isObject() ? schema : null);
                        String rebuilt = codecs.requestBody(provider, request);
                        boolean matches = RunRecordArchive.sha256(rebuilt.getBytes(StandardCharsets.UTF_8))
                                .equals(body.path("requestSha256").asText(""));
                        addVersioned(report, "provider-request", journal.taskId() + "/" + event.sequence(), matches, sameBuild,
                                "Single-call provider request serialization matches its pinned digest");
                    } catch (Exception invalid) {
                        addVersioned(report, "provider-request", journal.taskId() + "/" + event.sequence(), false, sameBuild,
                                "Request could not be rebuilt from its recorded fields");
                    }
                } else if ("TURN_REQUESTED".equals(event.type())) {
                    calls++;
                    String provider = providerByLease.getOrDefault(event.leaseId(), "");
                    boolean sameBuild = sameBuildByLease.getOrDefault(event.leaseId(), false);
                    try {
                        ConversationRequest request = ConversationRequestJournalCodec.decode(body.path("conversationRequest"));
                        String rebuilt = codecs.conversationBody(provider, request);
                        boolean matches = RunRecordArchive.sha256(rebuilt.getBytes(StandardCharsets.UTF_8))
                                .equals(body.path("requestSha256").asText(""));
                        addVersioned(report, "provider-request", journal.taskId() + "/" + event.sequence(), matches, sameBuild,
                                "Conversation request was rebuilt with its production provider serializer");
                    } catch (Exception invalid) {
                        addVersioned(report, "provider-request", journal.taskId() + "/" + event.sequence(), false, sameBuild,
                                "Conversation request could not be rebuilt from its typed journal record");
                    }
                }

                if ("CALL_COMPLETED".equals(event.type())) {
                    String provider = providerByLease.getOrDefault(event.leaseId(), "");
                    boolean sameBuild = sameBuildByLease.getOrDefault(event.leaseId(), false);
                    try {
                        JournalEvent requested = findPriorCallRequest(journal, event);
                        String requestedModel = requested.body().path("model").asText("");
                        ProviderResult parsed = codecs.parse(provider, body.path("responseBody").asText());
                        boolean matches = parsed.inputTokens() == body.path("inputTokens").asLong(-1)
                                && parsed.outputTokens() == body.path("outputTokens").asLong(-1)
                                && equalNullable(parsed.providerRequestId(), body.path("providerRequestId"))
                                && equalNullable(parsed.answeredModel(), body.path("answeredModel"));
                        addVersioned(report, "provider-response", journal.taskId() + "/" + event.sequence(), matches, sameBuild,
                                "Response fields match the production provider parser");
                        checkAnsweredModel(report, journal.taskId(), event.sequence(), requestedModel, parsed.answeredModel());
                        verifyUsageRow(report, journal, event, provider, requestedModel,
                                parsed.inputTokens(), parsed.outputTokens(), parsed.providerRequestId(),
                                requested.body().path("try").asInt(1), matchedAttemptRows);
                        schemaOutput(record, journal, event.leaseId(), parsed.output(), event.sequence(), report, sameBuild);
                    } catch (Exception invalid) {
                        addVersioned(report, "provider-response", journal.taskId() + "/" + event.sequence(), false, sameBuild,
                                "Response could not be parsed with the production provider parser");
                    }
                } else if ("TURN_COMPLETED".equals(event.type())) {
                    String provider = providerByLease.getOrDefault(event.leaseId(), "");
                    boolean sameBuild = sameBuildByLease.getOrDefault(event.leaseId(), false);
                    try {
                        JournalEvent requested = findPriorTurnRequest(journal, event);
                        ConversationRequest request = ConversationRequestJournalCodec.decode(requested.body().path("conversationRequest"));
                        ConversationTurn parsed = codecs.parseConversation(provider, body.path("responseBody").asText(), request);
                        JsonNode parsedCalls = JSON.valueToTree(parsed.toolCalls());
                        boolean matches = parsed.inputTokens() == body.path("inputTokens").asLong(-1)
                                && parsed.outputTokens() == body.path("outputTokens").asLong(-1)
                                && parsed.stopReason().name().equals(body.path("stopReason").asText())
                                && parsed.text().equals(body.path("text").asText())
                                && parsedCalls.equals(body.path("toolCalls"))
                                && equalNullable(parsed.providerRequestId(), body.path("providerRequestId"))
                                && equalNullable(parsed.answeredModel(), body.path("answeredModel"));
                        addVersioned(report, "provider-response", journal.taskId() + "/" + event.sequence(), matches, sameBuild,
                                "Conversation response fields match the production provider parser");
                        checkAnsweredModel(report, journal.taskId(), event.sequence(), request.model(), parsed.answeredModel());
                        verifyUsageRow(report, journal, event, provider, request.model(), parsed.inputTokens(), parsed.outputTokens(),
                                parsed.providerRequestId(), body.path("attemptCount").asInt(1), matchedAttemptRows);
                    } catch (Exception invalid) {
                        addVersioned(report, "provider-response", journal.taskId() + "/" + event.sequence(), false, sameBuild,
                                "Conversation response could not be parsed against its recorded request");
                    }
                } else if ("TURN_FAILED".equals(event.type())) {
                    verifyFailedTurnRow(report, journal, event, providerByLease.getOrDefault(event.leaseId(), ""),
                            modelByLease.getOrDefault(event.leaseId(), ""), matchedAttemptRows);
                }
            }
        }
        verifyUnmatchedAttemptRows(record, journals, matchedAttemptRows, report);
        if (calls == 0 && journals.isEmpty()) {
            boolean hasAttempts = record.path("tasks").isArray() && recordsHaveProviderAttempts(record.path("tasks"));
            report.add("provider-replay", "run", hasAttempts ? VerifyVerdict.UNVERIFIABLE : VerifyVerdict.PASS,
                    hasAttempts ? "Provider attempt rows exist without an exported journal" : "No provider calls require request or response replay");
        }
    }

    private void schemaOutput(JsonNode record, TaskJournal journal, String leaseId, String output, long sequence,
                              VerifyReport report, boolean sameBuild) {
        JsonNode started = null;
        String startedType = "";
        for (JournalEvent event : journal.events().values()) if (event.leaseId().equals(leaseId) && event.body() != null
                && ("WORKER_STARTED".equals(event.type()) || "LOOP_STARTED".equals(event.type()))) {
            started = event.body(); startedType = event.type(); break;
        }
        if (started == null || !"WORKER_STARTED".equals(startedType)) {
            // Loop responses are checked by their production parser; loop tool decisions remain a separate consistency check.
            return;
        }
        String role = started.path("executionRole").asText("");
        try {
            if ("PLANNER".equals(role)) {
                PlannerPlan plan = PlannerPlan.parse(output);
                JsonNode run = record.path("run");
                boolean testFirst = !run.path("testFirstGate").isMissingNode() && !run.path("testFirstGate").isNull();
                plan.validate(run.path("budgetUsd").asDouble(-1), testFirst);
                if (!plannerMatches(plan, record)) throw new IllegalArgumentException("Materialized planner graph differs");
            } else if (Set.of("IMPLEMENTATION", "BACKEND", "FRONTEND", "INDEPENDENT_TEST", "REPAIR").contains(role)) {
                PatchPlan.parse(output);
            } else if ("REVIEW".equals(role)) {
                if (!reviewMatches(output, journal.task())) throw new IllegalArgumentException("Materialized review evidence differs");
            } else return;
            report.add("output-schema", journal.taskId() + "/" + sequence, VerifyVerdict.PASS,
                    "Accepted provider output matches the production schema and materialized rows");
        } catch (Exception invalid) {
            addVersioned(report, "output-schema", journal.taskId() + "/" + sequence, false, sameBuild,
                    "Provider output does not match its schema or exported task and evidence rows");
        }
    }

    private static boolean plannerMatches(PlannerPlan plan, JsonNode record) {
        JsonNode criteria = record.path("criteria");
        if (!criteria.isArray() || criteria.size() != plan.acceptanceCriteria().size()) return false;
        Set<String> actualCriteria = new HashSet<>();
        criteria.forEach(item -> actualCriteria.add(item.path("statement").asText("")));
        if (!actualCriteria.equals(new HashSet<>(plan.acceptanceCriteria()))) return false;
        JsonNode tasks = record.path("tasks");
        if (!tasks.isArray()) return false;
        Map<String, JsonNode> actualTasks = new HashMap<>();
        tasks.forEach(item -> actualTasks.put(item.path("planKey").asText(""), item));
        if (actualTasks.size() != plan.tasks().size()) return false;
        for (PlannedTask expected : plan.tasks()) {
            JsonNode actual = actualTasks.get(expected.key());
            if (actual == null || !expected.role().equals(actual.path("role").asText())
                    || !expected.title().equals(actual.path("title").asText())
                    || !expected.requiredCapability().equals(actual.path("requiredCapability").asText())
                    || expected.attemptBudget() != actual.path("attemptBudget").asInt(-1)
                    || expected.budgetMicros() != actual.path("budgetMicros").asLong(-1)
                    || !listOfText(actual.path("dependencies")).equals(expected.dependencies())
                    || !listOfText(actual.path("ownedPaths")).equals(expected.ownedPaths())) return false;
        }
        return true;
    }

    private static boolean reviewMatches(String output, JsonNode task) throws Exception {
        JsonNode parsed = JSON.readTree(output);
        if (!parsed.isObject() || parsed.size() != 3 || !parsed.path("approved").isBoolean()
                || !parsed.path("summary").isTextual() || !parsed.path("criteria").isArray()) return false;
        JsonNode rows = task.path("reviewEvidence");
        if (!rows.isArray() || rows.isEmpty()) return false;
        JsonNode row = rows.get(rows.size() - 1);
        if (parsed.path("approved").asBoolean() != row.path("approved").asBoolean()
                || !parsed.path("summary").asText().equals(row.path("summary").asText())) return false;
        Map<String, JsonNode> actual = new HashMap<>();
        parsed.path("criteria").forEach(item -> actual.put(item.path("statement").asText(""), item));
        JsonNode expected = row.path("criteria");
        if (!expected.isArray() || actual.size() != expected.size()) return false;
        for (JsonNode criterion : expected) {
            JsonNode item = actual.get(criterion.path("statement").asText(""));
            if (item == null || !criterion.path("status").asText().equals(item.path("status").asText())
                    || !criterion.path("evidence").asText().equals(item.path("evidence").asText())) return false;
        }
        return true;
    }

    private void verifyUsageRow(VerifyReport report, TaskJournal journal, JournalEvent event, String provider,
                                String model, long inputTokens, long outputTokens, String requestId, int attemptCount,
                                Set<String> matchedAttemptRows) {
        // A local journal has no control-plane rows; request and response checks still run above.
        if (journal.task() == null) return;
        JsonNode attempts = journal.task().path("providerAttempts");
        if (!attempts.isArray()) {
            report.add("attempt-row-link", journal.taskId() + "/" + event.sequence(), VerifyVerdict.UNVERIFIABLE,
                    "Provider attempt rows are unavailable in this record");
            return;
        }
        List<JsonNode> candidates = new ArrayList<>();
        attempts.forEach(row -> {
            if (event.leaseId().equals(row.path("leaseId").asText()) && provider.equals(row.path("provider").asText())
                    && model.equals(row.path("model").asText())) candidates.add(row);
        });
        String material = requestId == null || requestId.isBlank()
                ? (event.body().path("correlationId").asText(event.leaseId())) : requestId;
        String digest = RunRecordArchive.sha256(material.getBytes(StandardCharsets.UTF_8));
        List<JsonNode> matchingRows = candidates.stream().filter(row -> row.path("inputTokens").asLong(-1) == inputTokens
                && row.path("outputTokens").asLong(-1) == outputTokens
                && row.path("answeredModel").asText("").equals(event.body().path("answeredModel").asText(""))
                && (attemptCount < 1 || row.path("attemptCount").asInt(-1) == attemptCount)
                && digest.equals(row.path("requestIdDigest").asText(""))).toList();
        if (matchingRows.size() == 1 && matchedAttemptRows.add(attemptRowKey(journal.taskId(), matchingRows.getFirst()))) {
            report.add("attempt-row-link", journal.taskId() + "/" + event.sequence(), VerifyVerdict.PASS,
                    "Provider call usage, identity, and lease match one exported attempt row");
            return;
        }
        boolean legacyRow = false;
        for (JsonNode row : attempts) if (provider.equals(row.path("provider").asText())
                && model.equals(row.path("model").asText()) && row.path("leaseId").asText("").isBlank()) legacyRow = true;
        report.add("attempt-row-link", journal.taskId() + "/" + event.sequence(),
                matchingRows.isEmpty() && legacyRow ? VerifyVerdict.UNVERIFIABLE : VerifyVerdict.FAIL,
                matchingRows.isEmpty() && legacyRow ? "Provider usage row predates lease linkage and cannot be assigned to this attempt"
                        : "Provider call has no unique matching task and lease usage row");
    }

    private void verifyFailedTurnRow(VerifyReport report, TaskJournal journal, JournalEvent event, String provider,
                                     String model, Set<String> matchedAttemptRows) {
        if (journal.task() == null) return;
        JsonNode attempts = journal.task().path("providerAttempts");
        if (!attempts.isArray()) {
            report.add("attempt-row-link", journal.taskId() + "/" + event.sequence(), VerifyVerdict.UNVERIFIABLE,
                    "Provider attempt rows are unavailable in this record");
            return;
        }
        JsonNode body = event.body();
        String correlation = body.path("correlationId").asText("");
        int attemptCount = body.path("attemptCount").asInt(-1);
        String category = body.path("category").asText("");
        boolean retryable = body.path("retryable").asBoolean(false);
        String digest = RunRecordArchive.sha256((correlation + "\0" + provider + "\0" + model + "\0"
                + attemptCount + "\0" + category).getBytes(StandardCharsets.UTF_8));
        List<JsonNode> matches = new ArrayList<>();
        attempts.forEach(row -> {
            if (event.leaseId().equals(row.path("leaseId").asText()) && provider.equals(row.path("provider").asText())
                    && model.equals(row.path("model").asText()) && digest.equals(row.path("requestIdDigest").asText())
                    && "FAILED".equals(row.path("outcome").asText()) && row.path("retryable").asBoolean() == retryable
                    && category.equals(row.path("category").asText()) && row.path("attemptCount").asInt(-1) == attemptCount
                    && row.path("inputTokens").asLong(-1) == 0 && row.path("outputTokens").asLong(-1) == 0
                    && row.path("estimatedCostMicros").asLong(-1) == 0 && !row.path("costKnown").asBoolean()) matches.add(row);
        });
        boolean linked = matches.size() == 1 && matchedAttemptRows.add(attemptRowKey(journal.taskId(), matches.getFirst()));
        report.add("attempt-row-link", journal.taskId() + "/" + event.sequence(), linked ? VerifyVerdict.PASS : VerifyVerdict.FAIL,
                linked ? "Failed provider turn matches its lease-bound attempt row and correlation digest"
                        : "Failed provider turn has no unique matching task and lease usage row");
    }

    private void verifyUnmatchedAttemptRows(JsonNode record, List<TaskJournal> journals, Set<String> matchedAttemptRows,
                                           VerifyReport report) {
        if (record == null || !record.path("tasks").isArray()) return;
        Map<String, TaskJournal> journalByTask = new HashMap<>();
        journals.forEach(journal -> journalByTask.put(journal.taskId(), journal));
        for (JsonNode task : record.path("tasks")) {
            String taskId = task.path("id").asText("");
            JsonNode attempts = task.path("providerAttempts");
            if (!attempts.isArray()) continue;
            for (JsonNode row : attempts) {
                if (matchedAttemptRows.contains(attemptRowKey(taskId, row))) continue;
                String leaseId = row.path("leaseId").asText("");
                String subject = taskId + "/" + (row.path("id").asText("").isBlank() ? leaseId : row.path("id").asText());
                if (leaseId.isBlank()) {
                    report.add("attempt-row-link", subject, VerifyVerdict.UNVERIFIABLE,
                            "Provider usage row predates 5a lease linkage");
                    continue;
                }
                TaskJournal journal = journalByTask.get(taskId);
                if (journal == null || verifyChain(new ArrayList<>(journal.events().values())).unverifiable()) {
                    report.add("attempt-row-link", subject, VerifyVerdict.UNVERIFIABLE,
                            "The provider attempt cannot be matched because its uploaded journal is missing or incomplete");
                    continue;
                }
                if (matchSingleCallFailure(row, journal, leaseId)) {
                    matchedAttemptRows.add(attemptRowKey(taskId, row));
                    report.add("attempt-row-link", subject, VerifyVerdict.PASS,
                            "Failed single-call provider attempts match their journal retries and lease digest");
                } else {
                    report.add("attempt-row-link", subject, VerifyVerdict.FAIL,
                            "An exported provider attempt row has no matching journal call or turn");
                }
            }
        }
    }

    private static boolean matchSingleCallFailure(JsonNode row, TaskJournal journal, String leaseId) {
        if (!"FAILED".equals(row.path("outcome").asText())
                || !Set.of("TRANSIENT_PROVIDER_FAILURE", "PERMANENT_PROVIDER_FAILURE").contains(row.path("category").asText()))
            return false;
        List<JournalEvent> failures = journal.events().values().stream().filter(event -> leaseId.equals(event.leaseId())
                && "CALL_FAILED".equals(event.type()) && event.body() != null).toList();
        if (failures.isEmpty()) return false;
        JournalEvent last = failures.getLast();
        int attempts = failures.size();
        boolean retryable = last.body().path("retryable").asBoolean(false);
        String category = retryable ? "TRANSIENT_PROVIDER_FAILURE" : "PERMANENT_PROVIDER_FAILURE";
        String provider = journal.events().values().stream().filter(event -> leaseId.equals(event.leaseId())
                && "WORKER_STARTED".equals(event.type()) && event.body() != null)
                .map(event -> event.body().path("provider").asText("")).findFirst().orElse("");
        String model = journal.events().values().stream().filter(event -> leaseId.equals(event.leaseId())
                && "WORKER_STARTED".equals(event.type()) && event.body() != null)
                .map(event -> event.body().path("model").asText("")).findFirst().orElse("");
        String digest = RunRecordArchive.sha256((leaseId + "\0" + provider + "\0" + model + "\0"
                + attempts + "\0" + category).getBytes(StandardCharsets.UTF_8));
        return provider.equals(row.path("provider").asText()) && model.equals(row.path("model").asText())
                && attempts == row.path("attemptCount").asInt(-1) && retryable == row.path("retryable").asBoolean()
                && category.equals(row.path("category").asText()) && digest.equals(row.path("requestIdDigest").asText())
                && row.path("inputTokens").asLong(-1) == 0 && row.path("outputTokens").asLong(-1) == 0
                && !failures.isEmpty() && !journal.events().values().stream().anyMatch(event -> leaseId.equals(event.leaseId())
                        && "CALL_COMPLETED".equals(event.type()));
    }

    private static String attemptRowKey(String taskId, JsonNode row) {
        String id = row.path("id").asText("");
        return taskId + "\0" + (id.isBlank() ? row.toString() : id);
    }

    private void verifyConsistency(JsonNode record, List<TaskJournal> journals, VerifyReport report) {
        for (TaskJournal journal : journals) {
            List<JournalEvent> events = journal.events().values().stream().filter(item -> item.body() != null).toList();
            ChainResult chain = verifyChain(new ArrayList<>(journal.events().values()));
            boolean incomplete = chain.failure() || chain.unverifiable();
            boolean toolsValid = true;
            boolean toolsUnverifiable = false;
            for (JournalEvent event : events) if ("TURN_COMPLETED".equals(event.type())) {
                int turn = event.body().path("turn").asInt(-1);
                List<JsonNode> requested = new ArrayList<>();
                events.stream().filter(candidate -> "TOOL_REQUESTED".equals(candidate.type())
                        && candidate.body().path("turn").asInt(-2) == turn).forEach(candidate -> requested.add(candidate.body()));
                JsonNode parsed = event.body().path("toolCalls");
                if (!parsed.isArray()) { toolsValid = false; continue; }
                if (parsed.size() != requested.size()) {
                    if (incomplete) toolsUnverifiable = true;
                    else toolsValid = false;
                    continue;
                }
                for (int index = 0; index < parsed.size(); index++) {
                    JsonNode call = parsed.get(index), request = requested.get(index);
                    if (!call.path("id").asText().equals(request.path("callId").asText())
                            || !call.path("name").asText().equals(request.path("tool").asText())
                            || !call.path("arguments").equals(request.path("arguments"))) toolsValid = false;
                }
            }
            VerifyVerdict toolVerdict = !toolsValid ? VerifyVerdict.FAIL : toolsUnverifiable || incomplete
                    ? VerifyVerdict.UNVERIFIABLE : VerifyVerdict.PASS;
            report.add("tool-call-order", journal.taskId(), toolVerdict,
                    toolVerdict == VerifyVerdict.PASS ? "Parsed tool calls match the subsequent requested tools in order"
                            : toolVerdict == VerifyVerdict.FAIL ? "Parsed tool calls differ from recorded tool requests"
                            : "Incomplete journal links prevent a complete tool-call ordering check");

            boolean deniedSafe = true;
            for (JournalEvent event : events) if ("TOOL_COMPLETED".equals(event.type())) {
                JsonNode body = event.body();
                if (Set.of("DENY", "REDIRECT").contains(body.path("decision").asText())) {
                    JsonNode outcome = body.path("outcome");
                    if (!"FAILED".equals(outcome.path("status").asText())
                            || outcome.path("postImages").isArray() && !outcome.path("postImages").isEmpty()) deniedSafe = false;
                }
            }
            VerifyVerdict denialVerdict = !deniedSafe ? VerifyVerdict.FAIL : incomplete
                    ? VerifyVerdict.UNVERIFIABLE : VerifyVerdict.PASS;
            report.add("denied-tool-no-write", journal.taskId(), denialVerdict,
                    denialVerdict == VerifyVerdict.PASS ? "Denied and redirected tools are failed and have no post-image"
                            : denialVerdict == VerifyVerdict.FAIL ? "A denied tool records a status or post-image that could change files"
                            : "Incomplete journal links prevent proving that denied tools caused no write");

            verifyLoopCounters(journal, events, report, incomplete);
        }
    }

    private void verifyLoopCounters(TaskJournal journal, List<JournalEvent> events, VerifyReport report, boolean incomplete) {
        List<JournalEvent> endings = events.stream().filter(event -> "LOOP_ENDED".equals(event.type())).toList();
        if (endings.isEmpty()) return;
        JournalEvent ended = endings.getLast();
        JsonNode counters = ended.body().path("counters");
        long turns = events.stream().filter(event -> "TURN_REQUESTED".equals(event.type())).count();
        long toolCalls = events.stream().filter(event -> "TOOL_REQUESTED".equals(event.type())).count();
        long input = 0, output = 0;
        for (JournalEvent event : events) if ("TURN_COMPLETED".equals(event.type())) {
            input += event.body().path("inputTokens").asLong(0);
            output += event.body().path("outputTokens").asLong(0);
        }
        boolean coreMatch = counters.path("turns").asLong(-1) == turns
                && counters.path("toolCalls").asLong(-1) == toolCalls
                && counters.path("inputTokens").asLong(-1) == input
                && counters.path("outputTokens").asLong(-1) == output;
        boolean observedExceeds = turns > counters.path("turns").asLong(-1)
                || toolCalls > counters.path("toolCalls").asLong(-1)
                || input > counters.path("inputTokens").asLong(-1)
                || output > counters.path("outputTokens").asLong(-1);
        VerifyVerdict countersVerdict = observedExceeds || !coreMatch && !incomplete ? VerifyVerdict.FAIL
                : incomplete ? VerifyVerdict.UNVERIFIABLE : VerifyVerdict.PASS;
        report.add("loop-counters", journal.taskId(), countersVerdict,
                countersVerdict == VerifyVerdict.PASS ? "Turn, tool-call, and token counters equal values derived from journal events"
                        : countersVerdict == VerifyVerdict.FAIL ? "Loop turn, tool-call, or token counters differ from a complete journal"
                        : "A journal gap or redacted record prevents an independent counter total");
        if (!counters.path("conversationBytes").canConvertToLong()) {
            report.add("loop-conversation-bytes", journal.taskId(), VerifyVerdict.UNVERIFIABLE,
                    "Conversation byte count is absent from the recorded terminal counters");
        } else {
            long derivedBytes = 0;
            boolean hasAppendRecords = false, validAppendRecords = true;
            for (JournalEvent event : events) if ("CONVERSATION_ITEM_ADDED".equals(event.type())) {
                hasAppendRecords = true;
                JsonNode body = event.body();
                String serializedItem = body.path("serializedItem").asText("");
                byte[] serialized = serializedItem.getBytes(StandardCharsets.UTF_8);
                if (!body.path("itemBytes").canConvertToLong() || body.path("itemBytes").asLong(-1) != serialized.length
                        || !RunRecordArchive.sha256(serialized).equals(body.path("itemSha256").asText(""))) {
                    validAppendRecords = false;
                    continue;
                }
                try { JSON.readTree(serializedItem); }
                catch (Exception invalid) { validAppendRecords = false; continue; }
                derivedBytes = Math.addExact(derivedBytes, serialized.length);
            }
            long recordedBytes = counters.path("conversationBytes").asLong(-1);
            VerifyVerdict appendVerdict = !hasAppendRecords ? VerifyVerdict.UNVERIFIABLE
                    : !validAppendRecords || derivedBytes > recordedBytes ? VerifyVerdict.FAIL
                    : incomplete ? VerifyVerdict.UNVERIFIABLE
                    : derivedBytes != recordedBytes ? VerifyVerdict.FAIL : VerifyVerdict.PASS;
            report.add("loop-conversation-bytes", journal.taskId(), appendVerdict,
                    !hasAppendRecords ? "This journal predates item-level append accounting"
                            : appendVerdict == VerifyVerdict.PASS ? "Exact serialized conversation appends reproduce the terminal byte counter"
                            : appendVerdict == VerifyVerdict.UNVERIFIABLE ? "Incomplete journal links prevent a complete conversation byte recount"
                            : "An append digest, byte count, or terminal conversation counter differs");
        }
        String budgetKind = ended.body().path("budgetKind").asText("");
        if ("BUDGET_STOP".equals(ended.body().path("outcome").asText()) && !budgetKind.isBlank()) {
            JsonNode started = events.stream().filter(event -> "LOOP_STARTED".equals(event.type())).map(JournalEvent::body).findFirst().orElse(null);
            if (started == null || !budgetReached(budgetKind, started, counters, ended.body(), events)) {
                report.add("budget-stop", journal.taskId(), VerifyVerdict.UNVERIFIABLE,
                        "The bounded stop could not be independently connected to its recorded limit");
            } else report.add("budget-stop", journal.taskId(), VerifyVerdict.PASS,
                    "Recorded stop reason names the budget reached by derived counters");
        }
    }

    private static boolean budgetReached(String kind, JsonNode started, JsonNode counters, JsonNode ended,
                                         List<JournalEvent> events) {
        JsonNode budget = started.path("budget");
        return switch (kind) {
            case "TOOL_CALLS" -> counters.path("toolCalls").asLong(-1) >= budget.path("maxToolCalls").asLong(Long.MAX_VALUE);
            case "TOKENS" -> counters.path("inputTokens").asLong(0) + counters.path("outputTokens").asLong(0)
                    >= budget.path("maxTokens").asLong(Long.MAX_VALUE);
            case "CONTEXT" -> counters.path("conversationBytes").asLong(-1) >= budget.path("maxConversationBytes").asLong(Long.MAX_VALUE);
            case "WALL_TIME" -> elapsedSeconds(started.path("at").asText(""), ended.path("at").asText(""),
                    budget.path("maxWallSeconds").asLong(-1));
            case "OUTPUT_LIMIT" -> events.stream().filter(event -> "TURN_COMPLETED".equals(event.type()))
                    .filter(event -> "MAX_TOKENS".equals(event.body().path("stopReason").asText()))
                    .anyMatch(completed -> events.stream().anyMatch(request -> "TURN_REQUESTED".equals(request.type())
                            && request.body().path("turn").asInt(-1) == completed.body().path("turn").asInt(-2)
                            && request.body().path("truncationRetry").asBoolean(false)));
            case "MONEY" -> false;
            default -> false;
        };
    }

    private static boolean elapsedSeconds(String startedAt, String endedAt, long maximum) {
        try { return maximum > 0 && Duration.between(Instant.parse(startedAt), Instant.parse(endedAt)).getSeconds() >= maximum; }
        catch (Exception invalid) { return false; }
    }

    private void verifyEvidence(JsonNode record, Map<String, JsonNode> artifacts,
                                RunRecordArchive archive, VerifyReport report) {
        JsonNode evidenceRows = record.path("verificationEvidence");
        if (!evidenceRows.isArray() || evidenceRows.isEmpty()) {
            report.add("evidence-bundles", "verificationEvidence", VerifyVerdict.PASS, "No verification bundle rows require checking");
            return;
        }
        for (JsonNode row : evidenceRows) {
            String id = row.path("id").asText("unknown");
            String artifactId = row.path("bundleArtifactId").asText("");
            JsonNode artifact = artifacts.get(artifactId);
            if (artifactId.isBlank() || artifact == null || !"VERIFICATION_BUNDLE".equals(artifact.path("artifactType").asText())
                    || !row.path("taskId").asText().equals(artifact.path("taskId").asText())
                    || !row.path("leaseId").asText().equals(artifact.path("leaseId").asText())) {
                report.add("evidence-bundles", id, VerifyVerdict.FAIL, "Verification evidence is not linked to its task and producing lease artifact");
                continue;
            }
            byte[] bytes = archive.entry(artifact.path("archivePath").asText(""));
            try {
                JsonNode bundle = bytes == null ? null : JSON.readTree(bytes);
                if (bundle == null) throw new IllegalArgumentException("missing");
                String outputDigest = RunRecordArchive.sha256(bundle.path("output").asText("").getBytes(StandardCharsets.UTF_8));
                String artifactReference = row.path("artifactReference").asText("");
                String storedBundleDigest = verificationBundleDigest(bundle, outputDigest, "");
                String rowBundleDigest = verificationBundleDigest(bundle, outputDigest, artifactReference);
                boolean matches = artifactReference.startsWith("artifact://")
                        && bundle.path("artifactReference").isNull()
                        && outputDigest.equals(bundle.path("outputDigest").asText())
                        && outputDigest.equals(row.path("outputDigest").asText())
                        && storedBundleDigest.equals(bundle.path("bundleDigest").asText())
                        && rowBundleDigest.equals(row.path("bundleDigest").asText())
                        && bundle.path("kind").asText().equals(row.path("kind").asText())
                        && nullable(bundle.path("gate")).equals(nullable(row.path("gate")))
                        && nullable(bundle.path("image")).equals(nullable(row.path("image")))
                        && listOfText(bundle.path("command")).equals(listOfText(row.path("command")))
                        && bundle.path("exitCode").asInt(Integer.MIN_VALUE) == row.path("exitCode").asInt(Integer.MAX_VALUE)
                        && bundle.path("timedOut").asBoolean(!row.path("timedOut").asBoolean()) == row.path("timedOut").asBoolean()
                        && bundle.path("output").asText().equals(row.path("output").asText())
                        && bundle.path("startedAt").asText().equals(row.path("startedAt").asText())
                        && bundle.path("finishedAt").asText().equals(row.path("finishedAt").asText());
            report.add("evidence-bundles", id, matches ? VerifyVerdict.PASS : VerifyVerdict.FAIL,
                        matches ? "Output and bundle digests reproduce and row links match the stored bundle" : "Evidence bundle digest or row link does not reproduce");
            } catch (Exception invalid) {
                report.add("evidence-bundles", id, VerifyVerdict.FAIL, "Verification evidence bundle is missing or invalid JSON");
            }
        }
    }

    private static String verificationBundleDigest(JsonNode bundle, String outputDigest, String artifactReference) {
        List<String> command = listOfText(bundle.path("command"));
        String gate = nullable(bundle.path("gate")), image = nullable(bundle.path("image"));
        String material = String.join("\u0000", bundle.path("kind").asText(), gate, image,
                String.join("\u001f", command), Integer.toString(bundle.path("exitCode").asInt(Integer.MIN_VALUE)),
                Boolean.toString(bundle.path("timedOut").asBoolean()), outputDigest,
                bundle.path("startedAt").asText(), bundle.path("finishedAt").asText(), artifactReference == null ? "" : artifactReference);
        return RunRecordArchive.sha256(material.getBytes(StandardCharsets.UTF_8));
    }

    private static void checkAnsweredModel(VerifyReport report, String taskId, long sequence, String requested, String answered) {
        boolean matches = answered != null && !answered.isBlank()
                && (answered.equals(requested) || answered.startsWith(requested));
        report.add("answered-model", taskId + "/" + sequence, matches ? VerifyVerdict.PASS : VerifyVerdict.FAIL,
                matches ? "Provider response model matches the requested model" : "Provider answered with a different or missing model");
    }

    private static JournalEvent findPriorTurnRequest(TaskJournal journal, JournalEvent completed) {
        int turn = completed.body().path("turn").asInt(-1);
        return journal.events().headMap(completed.sequence()).values().stream().filter(event -> event.body() != null
                        && "TURN_REQUESTED".equals(event.type()) && event.body().path("turn").asInt(-2) == turn)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Conversation request row is missing"));
    }

    private static JournalEvent findPriorCallRequest(TaskJournal journal, JournalEvent completed) {
        int call = completed.body().path("call").asInt(-1);
        int attempt = completed.body().path("try").asInt(-1);
        return journal.events().headMap(completed.sequence()).values().stream().filter(event -> event.body() != null
                        && "CALL_REQUESTED".equals(event.type()) && event.body().path("call").asInt(-2) == call
                        && event.body().path("try").asInt(-2) == attempt)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Provider request row is missing"));
    }

    private static void addVersioned(VerifyReport report, String id, String subject, boolean matches,
                                     boolean sameBuild, String passDetail) {
        report.add(id, subject, matches ? VerifyVerdict.PASS : sameBuild ? VerifyVerdict.FAIL : VerifyVerdict.UNVERIFIABLE,
                matches ? passDetail : sameBuild ? "Pinned harness reproduced a different result" : "Harness revision differs; this comparison is unverifiable");
    }

    private static boolean equalNullable(String parsed, JsonNode recorded) {
        if (parsed == null) return recorded == null || recorded.isNull() || recorded.asText("").isBlank();
        return parsed.equals(recorded == null ? null : recorded.asText(null));
    }

    private static List<JournalEvent> parseLines(byte[] bytes, String taskId, String expectedLease, boolean uploaded) throws IOException {
        if (bytes.length == 0 || bytes[bytes.length - 1] != '\n') throw new IOException("Journal segment must end at a whole line");
        String text;
        try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (Exception invalid) { throw new IOException("Journal is not UTF-8", invalid); }
        List<JournalEvent> events = new ArrayList<>();
        for (String line : text.substring(0, text.length() - 1).split("\n", -1)) {
            if (line.isBlank()) throw new IOException("Journal contains an empty line");
            JsonNode node;
            try { node = JSON.readTree(line); }
            catch (Exception invalid) { throw new IOException("Journal line is not JSON", invalid); }
            if (node == null || !node.isObject() || !node.path("seq").canConvertToLong()) throw new IOException("Journal identity is invalid");
            long seq = node.path("seq").asLong(-1);
            if (seq < 1) throw new IOException("Journal sequence is invalid");
            boolean redacted = node.path("redacted").asBoolean(false), omitted = node.path("omitted").asBoolean(false);
            if (redacted || omitted) {
                if (!uploaded || !node.path("sha256").asText("").matches("[0-9a-f]{64}")) throw new IOException("Journal marker is invalid");
                if (redacted && node.path("line").isTextual()) {
                    try { JSON.readTree(node.path("line").asText()); }
                    catch (Exception invalid) { throw new IOException("Redacted journal line is malformed", invalid); }
                }
                String lease = node.path("leaseId").asText("");
                if (expectedLease != null && !expectedLease.equals(lease)) throw new IOException("Journal segment contains another lease");
                events.add(new JournalEvent(seq, null, line, node.path("sha256").asText(), lease,
                        node.path("type").asText("UNKNOWN"), taskId, true));
            } else {
                String lease = node.path("leaseId").asText("");
                String type = node.path("type").asText("");
                String previous = node.path("prev").asText("");
                if (lease.isBlank() || !type.matches("[A-Z_]{1,80}") || !previous.matches("[0-9a-f]{64}"))
                    throw new IOException("Journal record identity is invalid");
                if (expectedLease != null && !expectedLease.equals(lease)) throw new IOException("Journal segment contains another lease");
                events.add(new JournalEvent(seq, node, line, RunRecordArchive.sha256(line.getBytes(StandardCharsets.UTF_8)),
                        lease, type, taskId, false));
            }
        }
        return List.copyOf(events);
    }

    private static ChainResult verifyChain(List<JournalEvent> events) {
        boolean unverifiable = false;
        boolean failure = false;
        long expected = 1;
        String previousHash = "0".repeat(64);
        for (JournalEvent event : events) {
            if (event.sequence() < expected) { failure = true; continue; }
            if (event.sequence() > expected) unverifiable = true;
            if (event.marker()) unverifiable = true;
            else if (event.sequence() == expected && !previousHash.equals(event.body().path("prev").asText())) failure = true;
            previousHash = event.originalHash();
            expected = event.sequence() + 1;
        }
        return new ChainResult(failure, unverifiable);
    }

    private static byte[] gunzip(byte[] content) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(content))) {
            return RunRecordArchive.boundedRead(gzip, MAX_JOURNAL_EXPANDED_BYTES);
        }
    }

    private static void requireRepository(Path path) throws IOException {
        if (path == null || Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Repository path must be an existing non-symlink directory");
        Path dotGit = path.resolve(".git");
        if (!Files.exists(dotGit, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Repository path is not a Git checkout");
    }

    private static Map<String, JsonNode> index(JsonNode values, String key) {
        Map<String, JsonNode> result = new HashMap<>();
        if (values != null && values.isArray()) for (JsonNode item : values) {
            String id = item.path(key).asText("");
            if (!id.isBlank()) result.putIfAbsent(id, item);
        }
        return result;
    }

    private static boolean recordsHaveProviderAttempts(JsonNode tasks) {
        for (JsonNode task : tasks) if (task.path("providerAttempts").isArray() && !task.path("providerAttempts").isEmpty()) return true;
        return false;
    }

    private static List<String> listOfText(JsonNode value) {
        List<String> result = new ArrayList<>();
        if (value != null && value.isArray()) value.forEach(item -> result.add(item.asText("")));
        return List.copyOf(result);
    }

    private static String nullable(JsonNode value) { return value == null || value.isNull() ? "" : value.asText(""); }

    private record JournalEvent(long sequence, JsonNode body, String rawLine, String originalHash,
                                String leaseId, String type, String taskId, boolean marker) { }
    private record TaskJournal(String taskId, JsonNode task, TreeMap<Long, JournalEvent> events) { }
    private record ChainResult(boolean failure, boolean unverifiable) { }
}
