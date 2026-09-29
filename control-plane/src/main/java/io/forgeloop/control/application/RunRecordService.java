package io.forgeloop.control.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.forgeloop.control.artifacts.ArtifactStore;
import io.forgeloop.control.domain.AcceptanceCriterion;
import io.forgeloop.control.domain.ArtifactMetadata;
import io.forgeloop.control.domain.ArtifactMetadataRepository;
import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.GithubPublication;
import io.forgeloop.control.domain.GithubPublicationRepository;
import io.forgeloop.control.domain.HumanEscalation;
import io.forgeloop.control.domain.HumanEscalationRepository;
import io.forgeloop.control.domain.ProviderAttempt;
import io.forgeloop.control.domain.RepairPackage;
import io.forgeloop.control.domain.ReviewEvidence;
import io.forgeloop.control.domain.ReviewEvidenceRepository;
import io.forgeloop.control.domain.TaskLease;
import io.forgeloop.control.domain.TaskLeaseRepository;
import io.forgeloop.control.domain.TestCheckEvidence;
import io.forgeloop.control.domain.TestCheckEvidenceRepository;
import io.forgeloop.control.domain.VerificationEvidence;
import io.forgeloop.control.domain.VerificationEvidenceRepository;
import io.forgeloop.control.domain.VerificationGate;
import io.forgeloop.control.security.OperatorContext;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds an export from immutable run rows and streams verified content-addressed artifacts. */
@Service
public class RunRecordService {
    private static final String SCHEMA = "forgeloop.run-record/1";
    private static final String ARTIFACT_PREFIX = "artifact://";
    private static final ObjectMapper JSON = new ObjectMapper().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);

    private final FeatureRunService runs;
    private final ArtifactMetadataRepository artifacts;
    private final ArtifactStore store;
    private final TaskLeaseRepository leases;
    private final VerificationEvidenceRepository verificationEvidence;
    private final TestCheckEvidenceRepository testEvidence;
    private final ReviewEvidenceRepository reviewEvidence;
    private final GithubPublicationRepository publications;
    private final HumanEscalationRepository escalations;
    private final AuditLedgerService audit;
    private final OperatorContext operators;
    private final RunExitMeaningService exitMeanings;
    private final long artifactMaxBytes;

    public RunRecordService(FeatureRunService runs, ArtifactMetadataRepository artifacts, ArtifactStore store,
                            TaskLeaseRepository leases, VerificationEvidenceRepository verificationEvidence,
                            TestCheckEvidenceRepository testEvidence, ReviewEvidenceRepository reviewEvidence,
                            GithubPublicationRepository publications, HumanEscalationRepository escalations,
                            AuditLedgerService audit, OperatorContext operators, RunExitMeaningService exitMeanings,
                            @Value("${forgeloop.artifacts.max-bytes:1048576}") long artifactMaxBytes) {
        if (artifactMaxBytes < 1 || artifactMaxBytes > 10 * 1024 * 1024)
            throw new IllegalArgumentException("Run-record artifact size limit is invalid");
        this.runs = runs;
        this.artifacts = artifacts;
        this.store = store;
        this.leases = leases;
        this.verificationEvidence = verificationEvidence;
        this.testEvidence = testEvidence;
        this.reviewEvidence = reviewEvidence;
        this.publications = publications;
        this.escalations = escalations;
        this.audit = audit;
        this.operators = operators;
        this.exitMeanings = exitMeanings;
        this.artifactMaxBytes = artifactMaxBytes;
    }

    /** Performs authorization and snapshots every database-backed value before HTTP streaming begins. */
    @Transactional
    public Export prepare(String runId) {
        operators.requireOperator();
        FeatureRun run = runs.get(runId);
        operators.requireOrganization(run.getOrganizationId());

        List<TaskLease> runLeases = leases.findByTask_Run_IdOrderByClaimedAtAsc(runId);
        Map<String, TaskLease> leasesById = new HashMap<>();
        runLeases.forEach(lease -> leasesById.put(lease.getId(), lease));
        Set<String> taskIds = run.getTasks().stream().map(DeliveryTask::getId).collect(java.util.stream.Collectors.toSet());
        List<ArtifactMetadata> runArtifacts = artifacts.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .filter(item -> run.isRunRecordEnabled() || !"RUN_JOURNAL".equals(item.getArtifactType()))
                .toList();
        List<Artifact> archiveArtifacts = new ArrayList<>();
        for (ArtifactMetadata item : runArtifacts) {
            TaskLease lease = leasesById.get(item.getLeaseId());
            if (!run.getId().equals(item.getRunId()) || !run.getOrganizationId().equals(item.getOrganizationId())
                    || !taskIds.contains(item.getTaskId()) || lease == null || !item.getTaskId().equals(lease.getTaskId()))
                throw new IllegalStateException("Run-record artifact is not linked to this run's task attempt");
            archiveArtifacts.add(archiveArtifact(item));
        }
        Instant exportedAt = Instant.now();
        Map<String, Object> record = record(run, runArtifacts, archiveArtifacts, runLeases, exportedAt, operators.subject());
        try {
            byte[] recordJson = JSON.writeValueAsBytes(record);
            return new Export(runId, run.getOrganizationId(), recordJson, sha256(recordJson), List.copyOf(archiveArtifacts));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Run record could not be serialized", failure);
        }
    }

    /** Writes one archive member at a time and audits only after a complete ZIP has been emitted. */
    public void writeArchive(Export export, OutputStream output) throws IOException {
        if (export == null || output == null) throw new IllegalArgumentException("Run-record export is incomplete");
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            writeEntry(zip, "record.json", export.recordJson());
            writeEntry(zip, "record.json.sha256", (export.recordSha256() + "\n").getBytes(StandardCharsets.US_ASCII));
            for (Artifact artifact : export.artifacts()) {
                String key = artifact.storageReference().substring(ARTIFACT_PREFIX.length());
                byte[] content = store.getVerified(key, artifact.sha256(), artifactMaxBytes);
                if (content.length != artifact.sizeBytes() || !sha256(content).equals(artifact.sha256()))
                    throw new IOException("Run-record artifact failed its size or digest check");
                writeEntry(zip, artifact.archivePath(), content);
            }
        }
        audit.record("RUN_RECORD_EXPORTED", "FEATURE_RUN", export.runId(), export.recordSha256());
    }

    private Map<String, Object> record(FeatureRun run, List<ArtifactMetadata> metadata, List<Artifact> archiveArtifacts,
                                       List<TaskLease> leaseEntities, Instant exportedAt, String exportedBy) {
        List<VerificationEvidence> verificationEntities = verificationEvidence.findByTask_Run_IdOrderByRecordedAtAsc(run.getId());
        List<TestCheckEvidence> testEntities = testEvidence.findByTask_Run_IdOrderByRecordedAtAsc(run.getId());
        List<ReviewEvidence> reviewEntities = reviewEvidence.findByTask_Run_IdOrderByRecordedAtAsc(run.getId());
        Map<String, ArtifactMetadata> artifactByStorage = new HashMap<>();
        metadata.forEach(item -> artifactByStorage.put(item.getStorageReference(), item));
        Map<String, List<TaskLease>> leasesByTask = group(leaseEntities, TaskLease::getTaskId);
        Map<String, List<VerificationEvidence>> verificationByTask = group(verificationEntities, VerificationEvidence::getTaskId);
        Map<String, List<TestCheckEvidence>> testByTask = group(testEntities, TestCheckEvidence::getTaskId);
        Map<String, List<ReviewEvidence>> reviewByTask = group(reviewEntities, ReviewEvidence::getTaskId);
        Map<String, List<Artifact>> artifactsByTask = group(archiveArtifacts, Artifact::taskId);
        List<Map<String, Object>> taskRows = run.getTasks().stream().map(task -> task(task,
                leasesByTask.getOrDefault(task.getId(), List.of()),
                verificationByTask.getOrDefault(task.getId(), List.of()),
                testByTask.getOrDefault(task.getId(), List.of()),
                reviewByTask.getOrDefault(task.getId(), List.of()),
                artifactsByTask.getOrDefault(task.getId(), List.of()), artifactByStorage)).toList();
        List<Map<String, Object>> leaseRows = leaseEntities.stream().map(this::lease).toList();
        List<Map<String, Object>> verificationRows = verificationEntities.stream().map(item -> verification(item, artifactByStorage)).toList();
        List<Map<String, Object>> testRows = testEntities.stream().map(item -> testCheck(item, artifactByStorage)).toList();
        List<Map<String, Object>> reviewRows = reviewEntities.stream().map(this::review).toList();
        GithubPublication publication = publications.findByFeatureRunId(run.getId()).orElse(null);
        List<Map<String, Object>> artifactRows = new ArrayList<>();
        for (int i = 0; i < metadata.size(); i++) artifactRows.add(artifact(metadata.get(i), archiveArtifacts.get(i)));
        RunExitMeaning meaning = exitMeanings.derive(List.of(run)).get(run.getId());

        return map("schema", SCHEMA, "exportedAt", exportedAt.toString(), "exportedBy", exportedBy,
                "run", map("id", run.getId(), "organizationId", run.getOrganizationId(), "repository", run.getRepository(),
                        "sourceRef", run.getSourceRef(), "baseBranch", run.getBaseBranch(), "title", run.getTitle(),
                "specification", run.getSpecification(), "specificationSha256", sha256(java.util.Objects.toString(run.getSpecification(), "").getBytes(StandardCharsets.UTF_8)),
                        "budgetUsd", run.getBudgetUsd(), "spentCostMicros", run.getSpentCostMicros(),
                        "harnessProfile", run.getHarnessProfile(), "policyRevision", run.getPolicyRevision(),
                        "policySnapshot", run.getPolicySnapshot(), "policySnapshotSha256", run.getPolicySnapshotSha256(),
                        "runRecord", run.isRunRecordEnabled(), "runRecordEnabled", run.isRunRecordEnabled(),
                        "testFirstGate", run.getTestFirstGate(), "testPathGlobs", run.getTestPathGlobs(),
                        "state", run.getState().name(), "exitMeaning", meaning == null || meaning.outcome() == null ? null : meaning.outcome().name(),
                        "exitReason", meaning == null ? null : meaning.reason(), "createdAt", run.getCreatedAt(),
                        "approved", run.isApproved(), "approvedAt", run.getApprovedAt(), "approvedBy", run.getApprovedBy()),
                "gates", run.getGates().stream().map(this::gate).toList(),
                "criteria", run.getCriteria().stream().map(this::criterion).toList(), "tasks", taskRows,
                "leases", leaseRows, "verificationEvidence", verificationRows, "testCheckEvidence", testRows,
                "reviewEvidence", reviewRows, "publication", publication == null ? null : publication(publication),
                "escalations", escalations.findByRunIdOrderByCreatedAtAsc(run.getId()).stream().map(this::escalation).toList(),
                "audit", audit.events("FEATURE_RUN", run.getId()).stream().map(this::auditEntry).toList(),
                "artifacts", artifactRows, "files", archiveArtifacts.stream().map(this::file).toList());
    }

    private Map<String, Object> task(DeliveryTask task, List<TaskLease> taskLeases,
                                     List<VerificationEvidence> verificationRows,
                                     List<TestCheckEvidence> testRows, List<ReviewEvidence> reviewRows,
                                     List<Artifact> taskArtifacts, Map<String, ArtifactMetadata> artifactByStorage) {
        List<Map<String, Object>> attempts = task.getProviderAttempts().stream().map(this::providerAttempt).toList();
        List<Map<String, Object>> repairs = task.getRepairPackages().stream().map(this::repair).toList();
        List<Map<String, Object>> journals = taskArtifacts.stream().filter(item -> "RUN_JOURNAL".equals(item.artifactType()))
                .map(this::journal).toList();
        return map("id", task.getId(), "planKey", task.getPlanKey(), "role", task.getRole(), "title", task.getTitle(),
                "requiredCapability", task.getRequiredCapability(), "state", task.getState().name(),
                "attemptBudget", task.getAttemptBudget(), "attemptCount", task.getAttempts(), "budgetMicros", task.getBudgetMicros(),
                "spentCostMicros", task.getSpentCostMicros(), "changeSha", task.getChangeSha(),
                "ownedPaths", task.getOwnedPaths(), "dependencies", task.getDependencyKeys(),
                "attempts", taskLeases.stream().map(this::lease).toList(), "providerAttempts", attempts, "repairPackages", repairs,
                "verificationEvidence", verificationRows.stream().map(item -> verification(item, artifactByStorage)).toList(),
                "testCheckEvidence", testRows.stream().map(item -> testCheck(item, artifactByStorage)).toList(),
                "reviewEvidence", reviewRows.stream().map(this::review).toList(), "journal", journals);
    }

    private Map<String, Object> providerAttempt(ProviderAttempt attempt) {
        return map("id", attempt.getId(), "taskId", attempt.getTaskId(), "leaseId", attempt.getLeaseId(),
                "runnerId", attempt.getRunnerId(), "provider", attempt.getProvider(), "model", attempt.getModel(),
                "answeredModel", attempt.getAnsweredModel(), "requestIdDigest", attempt.getRequestIdDigest(),
                "inputTokens", attempt.getInputTokens(), "outputTokens", attempt.getOutputTokens(),
                "attemptCount", attempt.getAttemptCount(), "estimatedCostMicros", attempt.getEstimatedCostMicros(),
                "costKnown", attempt.isCostKnown(), "outcome", attempt.getOutcome(), "retryable", attempt.isRetryable(),
                "category", attempt.getCategory(), "recordedAt", attempt.getRecordedAt());
    }

    private Map<String, Object> lease(TaskLease lease) {
        return map("id", lease.getId(), "taskId", lease.getTaskId(), "runnerId", lease.getRunnerId(),
                "claimedAt", lease.getClaimedAt().toString(), "expiresAt", lease.getExpiresAt(),
                "acknowledgedAt", instant(lease.getAcknowledgedAt()), "closedAt", instant(lease.getCompletedAt()),
                "acknowledged", lease.isAcknowledged(), "completed", lease.isCompleted(),
                "runnerRevision", lease.getRunnerRevision(), "runnerJarSha256", lease.getRunnerJarSha256(),
                "inputRefs", lease.getInputRefs(), "resultSha", lease.getResultSha(),
                "outcome", lease.getOutcome() == null ? null : lease.getOutcome().name(),
                "outcomeCategory", lease.getOutcomeCategory());
    }

    private Map<String, Object> verification(VerificationEvidence evidence, Map<String, ArtifactMetadata> artifactByStorage) {
        ArtifactMetadata bundle = evidence.getArtifactReference() == null ? null : artifactByStorage.get(evidence.getArtifactReference());
        return map("id", evidence.getId(), "taskId", evidence.getTaskId(), "leaseId", evidence.getLeaseId(),
                "runnerId", evidence.getRunnerId(), "kind", evidence.getKind(), "gate", evidence.getGate(),
                "image", evidence.getImage(), "command", evidence.getCommand(), "exitCode", evidence.getExitCode(),
                "timedOut", evidence.isTimedOut(), "output", evidence.getOutput(), "digest", evidence.getDigest(),
                "startedAt", evidence.getStartedAt(), "finishedAt", evidence.getFinishedAt(),
                "recordedAt", evidence.getRecordedAt(), "artifactReference", evidence.getArtifactReference(),
                "outputDigest", evidence.getOutputDigest(), "bundleDigest", evidence.getBundleDigest(),
                "targetSha", evidence.getTargetSha(), "imageId", evidence.getImageId(),
                "outputTruncated", evidence.getOutputTruncated(), "bundleArtifactId", bundle == null ? null : bundle.getId());
    }

    private Map<String, Object> testCheck(TestCheckEvidence evidence, Map<String, ArtifactMetadata> artifactByStorage) {
        ArtifactMetadata bundle = evidence.getArtifactReference() == null ? null : artifactByStorage.get(evidence.getArtifactReference());
        return map("id", evidence.getId(), "taskId", evidence.getTaskId(), "leaseId", evidence.getLeaseId(),
                "runnerId", evidence.getRunnerId(), "kind", evidence.getKind(), "gate", evidence.getGate(),
                "image", evidence.getImage(), "command", evidence.getCommand(), "targetSha", evidence.getTargetSha(),
                "parentSha", evidence.getParentSha(), "verdict", evidence.getVerdict(), "reason", evidence.getReason(),
                "beforeStatus", evidence.getBeforeStatus(), "afterStatus", evidence.getAfterStatus(),
                "beforeExitCode", evidence.getBeforeExitCode(), "afterExitCode", evidence.getAfterExitCode(),
                "beforeTimedOut", evidence.isBeforeTimedOut(), "afterTimedOut", evidence.isAfterTimedOut(),
                "beforeCounts", counts(evidence.getBeforeTotal(), evidence.getBeforePassed(), evidence.getBeforeFailed(), evidence.getBeforeErrored(), evidence.getBeforeSkipped()),
                "afterCounts", counts(evidence.getAfterTotal(), evidence.getAfterPassed(), evidence.getAfterFailed(), evidence.getAfterErrored(), evidence.getAfterSkipped()),
                "redTests", evidence.getRedTests(), "failingTests", evidence.getFailingTests(),
                "classifiedTests", evidence.getClassifiedTests(), "changedFiles", evidence.getChangedFiles(),
                "beforeOutcomeDigest", evidence.getBeforeOutcomeDigest(), "afterOutcomeDigest", evidence.getAfterOutcomeDigest(),
                "beforeOutputDigest", evidence.getBeforeOutputDigest(), "afterOutputDigest", evidence.getAfterOutputDigest(),
                "artifactReference", evidence.getArtifactReference(), "bundleDigest", evidence.getBundleDigest(),
                "digest", evidence.getDigest(), "recordedAt", evidence.getRecordedAt(),
                "bundleArtifactId", bundle == null ? null : bundle.getId());
    }

    private static Map<String, Object> counts(Integer total, Integer passed, Integer failed, Integer errored, Integer skipped) {
        return map("total", total, "passed", passed, "failed", failed, "errored", errored, "skipped", skipped);
    }

    private Map<String, Object> review(ReviewEvidence evidence) {
        return map("id", evidence.getId(), "taskId", evidence.getTaskId(), "runnerId", evidence.getRunnerId(),
                "approved", evidence.isApproved(), "summary", evidence.getSummary(), "digest", evidence.getDigest(),
                "recordedAt", evidence.getRecordedAt(), "criteria", evidence.getCriteria().stream()
                        .map(item -> map("id", item.getId(), "statement", item.getStatement(), "status", item.getStatus(), "evidence", item.getEvidence())).toList());
    }

    private Map<String, Object> repair(RepairPackage repair) {
        return map("id", repair.getId(), "attempt", repair.getAttempt(), "failureCategory", repair.getFailureCategory(),
                "changeSha", repair.getChangeSha(), "evidenceDigest", repair.getEvidenceDigest(),
                "ownedPaths", repair.getOwnedPaths(), "acceptanceCriteria", repair.getAcceptanceCriteria(),
                "failingTests", repair.getFailingTests(), "createdAt", repair.getCreatedAt());
    }

    private Map<String, Object> gate(VerificationGate gate) {
        return map("id", gate.getId(), "name", gate.getName(), "required", gate.isRequired(), "state", gate.getState(),
                "kind", gate.getKind(), "imageDigest", gate.getImageDigest(), "command", gate.getCommand(),
                "networkPolicy", gate.getNetworkPolicy(), "timeoutSeconds", gate.getTimeoutSeconds(),
                "criterionCoverage", gate.getCriterionCoverage(), "testReport", gate.getTestReport());
    }

    private Map<String, Object> criterion(AcceptanceCriterion criterion) {
        return map("id", criterion.getId(), "statement", criterion.getStatement(), "coverageState", criterion.getCoverageState());
    }

    private Map<String, Object> publication(GithubPublication publication) {
        return map("id", publication.getId(), "repository", publication.getRepository(), "branch", publication.getBranch(),
                "headSha", publication.getHeadSha(), "pullRequestNumber", publication.getPullRequestNumber(),
                "checkRunId", publication.getCheckRunId(), "deliveredAt", instant(publication.getDeliveredAt()),
                "autoMergeRequested", publication.isAutoMergeRequested(), "mergedAt", instant(publication.getMergedAt()), "mergeSha", publication.getMergeSha());
    }

    private Map<String, Object> escalation(HumanEscalation escalation) {
        return map("id", escalation.getId(), "taskId", escalation.getTaskId(), "reason", escalation.getReason(),
                "severity", escalation.getSeverity(), "status", escalation.getStatus(), "summary", escalation.getSummary(),
                "createdAt", escalation.getCreatedAt(), "acknowledgedAt", escalation.getAcknowledgedAt(),
                "acknowledgedBy", escalation.getAcknowledgedBy(), "resolvedAt", escalation.getResolvedAt(),
                "resolvedBy", escalation.getResolvedBy());
    }

    private Map<String, Object> auditEntry(io.forgeloop.control.domain.AuditLedgerEntry entry) {
        return map("id", entry.getId(), "actor", entry.getActor(), "action", entry.getAction(),
                "resourceType", entry.getResourceType(), "resourceId", entry.getResourceId(),
                "payloadDigest", entry.getPayloadDigest(), "occurredAt", entry.getOccurredAt());
    }

    private Artifact archiveArtifact(ArtifactMetadata item) {
        if (item.getStorageReference() == null || !item.getStorageReference().startsWith(ARTIFACT_PREFIX)
                || item.getStorageReference().length() <= ARTIFACT_PREFIX.length())
            throw new IllegalStateException("Run-record artifact reference is invalid");
        if (item.getId() == null || !item.getId().matches("[A-Za-z0-9-]{1,64}")
                || item.getDisplayName() == null || !item.getDisplayName().matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,159}")
                || item.getSha256() == null || !item.getSha256().matches("[0-9a-f]{64}")
                || item.getSizeBytes() < 0 || item.getSizeBytes() > artifactMaxBytes)
            throw new IllegalStateException("Run-record artifact metadata is invalid");
        String key = item.getStorageReference().substring(ARTIFACT_PREFIX.length());
        String expectedKey = String.join("/", item.getOrganizationId(), item.getRunId(), item.getTaskId(), item.getLeaseId(), item.getDisplayName());
        if (!key.equals(expectedKey) || key.contains("\\") || java.util.Arrays.stream(key.split("/", -1))
                .anyMatch(part -> part.isBlank() || part.equals(".") || part.equals("..") || !part.matches("[A-Za-z0-9._:-]{1,160}")))
            throw new IllegalStateException("Run-record artifact storage key is invalid");
        return new Artifact(item.getId(), item.getTaskId(), item.getLeaseId(), item.getArtifactType(), item.getDisplayName(),
                "artifacts/" + item.getId() + "/" + item.getDisplayName(), item.getStorageReference(), item.getSizeBytes(), item.getSha256());
    }

    private Map<String, Object> artifact(ArtifactMetadata metadata, Artifact artifact) {
        return map("id", artifact.id(), "taskId", artifact.taskId(), "leaseId", artifact.leaseId(),
                "artifactType", artifact.artifactType(), "displayName", artifact.displayName(),
                "archivePath", artifact.archivePath(), "contentType", metadata.getContentType(),
                "sizeBytes", artifact.sizeBytes(), "sha256", artifact.sha256(),
                "createdAt", metadata.getCreatedAt(), "retentionClass", metadata.getRetentionClass(), "retainUntil", metadata.getRetainUntil());
    }

    private Map<String, Object> file(Artifact artifact) {
        return map("path", artifact.archivePath(), "artifactId", artifact.id(), "taskId", artifact.taskId(),
                "leaseId", artifact.leaseId(), "artifactType", artifact.artifactType(),
                "sizeBytes", artifact.sizeBytes(), "sha256", artifact.sha256());
    }

    private Map<String, Object> journal(Artifact artifact) {
        java.util.regex.Matcher sequence = java.util.regex.Pattern.compile("journal-([0-9]{1,10})-([0-9]{1,10})\\.jsonl\\.gz")
                .matcher(artifact.displayName());
        if (!sequence.matches()) throw new IllegalStateException("Run journal segment name is invalid");
        return map("artifactId", artifact.id(), "displayName", artifact.displayName(), "leaseId", artifact.leaseId(),
                "sha256", artifact.sha256(), "sizeBytes", artifact.sizeBytes(),
                "firstSeq", Long.parseLong(sequence.group(1)), "lastSeq", Long.parseLong(sequence.group(2)));
    }

    private static <T> Map<String, List<T>> group(List<T> values, java.util.function.Function<T, String> key) {
        Map<String, List<T>> grouped = new HashMap<>();
        values.forEach(value -> grouped.computeIfAbsent(key.apply(value), ignored -> new ArrayList<>()).add(value));
        grouped.replaceAll((ignored, items) -> List.copyOf(items));
        return grouped;
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new TreeMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }

    private static void writeEntry(ZipOutputStream zip, String path, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception failure) { throw new IllegalStateException("SHA-256 unavailable", failure); }
    }

    private static String instant(java.time.Instant value) { return value == null ? null : value.toString(); }

    public record Artifact(String id, String taskId, String leaseId, String artifactType, String displayName,
                           String archivePath, String storageReference, long sizeBytes, String sha256) { }

    public record Export(String runId, String organizationId, byte[] recordJson, String recordSha256, List<Artifact> artifacts) {
        public Export {
            recordJson = recordJson.clone();
            artifacts = List.copyOf(artifacts);
        }
        @Override public byte[] recordJson() { return recordJson.clone(); }
    }
}
