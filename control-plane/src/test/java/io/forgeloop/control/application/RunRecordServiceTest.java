package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.forgeloop.control.artifacts.ArtifactStore;
import io.forgeloop.control.domain.ArtifactMetadata;
import io.forgeloop.control.domain.ArtifactMetadataRepository;
import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.GithubPublicationRepository;
import io.forgeloop.control.domain.HumanEscalationRepository;
import io.forgeloop.control.domain.ReviewEvidenceRepository;
import io.forgeloop.control.domain.RunState;
import io.forgeloop.control.domain.TaskLeaseRepository;
import io.forgeloop.control.domain.TaskLease;
import io.forgeloop.control.domain.TaskState;
import io.forgeloop.control.domain.TestCheckEvidenceRepository;
import io.forgeloop.control.domain.VerificationEvidenceRepository;
import io.forgeloop.control.security.OperatorContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class RunRecordServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private FeatureRunService runs;
    private ArtifactMetadataRepository artifacts;
    private ArtifactStore store;
    private TaskLeaseRepository leases;
    private VerificationEvidenceRepository verification;
    private TestCheckEvidenceRepository testEvidence;
    private ReviewEvidenceRepository review;
    private GithubPublicationRepository publications;
    private HumanEscalationRepository escalations;
    private AuditLedgerService audit;
    private OperatorContext operators;
    private RunExitMeaningService exitMeanings;
    private FeatureRun run;
    private RunRecordService records;

    @BeforeEach
    void setUp() {
        runs = mock(FeatureRunService.class);
        artifacts = mock(ArtifactMetadataRepository.class);
        store = mock(ArtifactStore.class);
        leases = mock(TaskLeaseRepository.class);
        verification = mock(VerificationEvidenceRepository.class);
        testEvidence = mock(TestCheckEvidenceRepository.class);
        review = mock(ReviewEvidenceRepository.class);
        publications = mock(GithubPublicationRepository.class);
        escalations = mock(HumanEscalationRepository.class);
        audit = mock(AuditLedgerService.class);
        operators = mock(OperatorContext.class);
        exitMeanings = mock(RunExitMeaningService.class);
        run = mock(FeatureRun.class);

        when(runs.get("run-1")).thenReturn(run);
        when(run.getId()).thenReturn("run-1");
        when(run.getOrganizationId()).thenReturn("org-1");
        when(run.getRepository()).thenReturn("JRH89/example");
        when(run.getSourceRef()).thenReturn("refs/heads/feature");
        when(run.getBaseBranch()).thenReturn("main");
        when(run.getTitle()).thenReturn("Record export test");
        when(run.getSpecification()).thenReturn("Keep a verifiable record.");
        when(run.getState()).thenReturn(RunState.COMPLETE);
        when(run.getCreatedAt()).thenReturn("2026-09-29T12:00:00Z");
        when(run.isRunRecordEnabled()).thenReturn(true);
        when(run.getTasks()).thenReturn(java.util.List.of());
        when(run.getGates()).thenReturn(java.util.List.of());
        when(run.getCriteria()).thenReturn(java.util.List.of());
        when(operators.subject()).thenReturn("operator-7");
        when(leases.findByTask_Run_IdOrderByClaimedAtAsc("run-1")).thenReturn(java.util.List.of());
        when(artifacts.findByRunIdOrderByCreatedAtAsc("run-1")).thenReturn(java.util.List.of());
        when(verification.findByTask_Run_IdOrderByRecordedAtAsc("run-1")).thenReturn(java.util.List.of());
        when(testEvidence.findByTask_Run_IdOrderByRecordedAtAsc("run-1")).thenReturn(java.util.List.of());
        when(review.findByTask_Run_IdOrderByRecordedAtAsc("run-1")).thenReturn(java.util.List.of());
        when(publications.findByFeatureRunId("run-1")).thenReturn(java.util.Optional.empty());
        when(escalations.findByRunIdOrderByCreatedAtAsc("run-1")).thenReturn(java.util.List.of());
        when(audit.events("FEATURE_RUN", "run-1")).thenReturn(java.util.List.of());
        when(exitMeanings.derive(java.util.List.of(run))).thenReturn(Map.of("run-1", new RunExitMeaning(
                io.forgeloop.control.domain.AttemptOutcome.CLEAN, "COMPLETED")));

        records = new RunRecordService(runs, artifacts, store, leases, verification, testEvidence, review,
                publications, escalations, audit, operators, exitMeanings, 1024);
    }

    @Test
    void recordContainsExportIdentityAndSpecificationDigest() throws Exception {
        RunRecordService.Export export = records.prepare("run-1");
        JsonNode record = JSON.readTree(export.recordJson());
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("Keep a verifiable record.".getBytes(StandardCharsets.UTF_8)));

        assertEquals("forgeloop.run-record/1", record.path("schema").asText());
        assertEquals("operator-7", record.path("exportedBy").asText());
        assertTrue(record.path("exportedAt").isTextual());
        assertEquals(expected, record.path("run").path("specificationSha256").asText());
        assertEquals("CLEAN", record.path("run").path("exitMeaning").asText());
        assertEquals("COMPLETED", record.path("run").path("exitReason").asText());
        assertTrue(record.path("files").isArray() && record.path("files").isEmpty());
    }

    @Test
    void streamsARecordAndDigestSidecarInZip() throws Exception {
        RunRecordService.Export export = records.prepare("run-1");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        records.writeArchive(export, output);
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                entries.put(entry.getName(), zip.readAllBytes());
        }

        assertTrue(entries.containsKey("record.json"));
        assertEquals(export.recordSha256() + "\n", new String(entries.get("record.json.sha256"), StandardCharsets.US_ASCII));
        assertEquals(export.recordSha256(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(entries.get("record.json"))));
    }

    @Test
    void streamsVerifiedArtifactBytesAtTheManifestPath() throws Exception {
        byte[] content = "verified evidence".getBytes(StandardCharsets.UTF_8);
        String digest = sha(content);
        configureArtifact(content.length, digest, content);
        RunRecordService.Export export = records.prepare("run-1");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        records.writeArchive(export, output);

        Map<String, byte[]> entries = zipEntries(output.toByteArray());
        assertTrue(java.util.Arrays.equals(content, entries.get("artifacts/artifact-1/evidence.json")));
        verify(audit).record("RUN_RECORD_EXPORTED", "FEATURE_RUN", "run-1", export.recordSha256());
    }

    @Test
    void corruptStoredArtifactFailsClosedBeforeExportAudit() throws Exception {
        byte[] expected = "verified evidence".getBytes(StandardCharsets.UTF_8);
        byte[] corrupt = "modified evidence".getBytes(StandardCharsets.UTF_8);
        configureArtifact(expected.length, sha(expected), corrupt);
        RunRecordService.Export export = records.prepare("run-1");

        assertThrows(IOException.class, () -> records.writeArchive(export, new ByteArrayOutputStream()));
        verify(audit, never()).record("RUN_RECORD_EXPORTED", "FEATURE_RUN", "run-1", export.recordSha256());
    }

    @Test
    void rejectsNonOperatorsBeforeLookingUpTheRun() {
        doThrow(new AccessDeniedException("viewer")).when(operators).requireOperator();

        assertThrows(AccessDeniedException.class, () -> records.prepare("run-1"));
        verify(runs, never()).get("run-1");
    }

    @Test
    void rejectsCrossTenantRunBeforeLoadingArtifacts() {
        doThrow(new AccessDeniedException("wrong tenant")).when(operators).requireOrganization("org-1");

        assertThrows(AccessDeniedException.class, () -> records.prepare("run-1"));
        verify(artifacts, never()).findByRunIdOrderByCreatedAtAsc("run-1");
    }

    private void configureArtifact(long size, String digest, byte[] storedBytes) {
        DeliveryTask task = mock(DeliveryTask.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getPlanKey()).thenReturn("writer");
        when(task.getRole()).thenReturn("IMPLEMENTATION");
        when(task.getTitle()).thenReturn("Example writer");
        when(task.getRequiredCapability()).thenReturn("provider");
        when(task.getState()).thenReturn(TaskState.CHANGE_READY);
        when(task.getAttemptBudget()).thenReturn(2);
        when(task.getAttempts()).thenReturn(1);
        when(task.getBudgetMicros()).thenReturn(100_000L);
        when(task.getSpentCostMicros()).thenReturn(0L);
        when(task.getOwnedPaths()).thenReturn(java.util.List.of("src"));
        when(task.getDependencyKeys()).thenReturn(java.util.List.of());
        when(task.getProviderAttempts()).thenReturn(java.util.List.of());
        when(task.getRepairPackages()).thenReturn(java.util.List.of());
        when(run.getTasks()).thenReturn(java.util.List.of(task));
        TaskLease lease = mock(TaskLease.class);
        when(lease.getId()).thenReturn("lease-1");
        when(lease.getTaskId()).thenReturn("task-1");
        when(lease.getRunnerId()).thenReturn("runner-1");
        when(lease.getClaimedAt()).thenReturn(Instant.parse("2026-09-29T12:00:00Z"));
        when(lease.getExpiresAt()).thenReturn("2026-09-29T12:15:00Z");
        when(lease.getCompletedAt()).thenReturn(null);
        when(leases.findByTask_Run_IdOrderByClaimedAtAsc("run-1")).thenReturn(java.util.List.of(lease));
        ArtifactMetadata metadata = mock(ArtifactMetadata.class);
        when(metadata.getId()).thenReturn("artifact-1");
        when(metadata.getOrganizationId()).thenReturn("org-1");
        when(metadata.getRunId()).thenReturn("run-1");
        when(metadata.getTaskId()).thenReturn("task-1");
        when(metadata.getLeaseId()).thenReturn("lease-1");
        when(metadata.getStorageReference()).thenReturn("artifact://org-1/run-1/task-1/lease-1/evidence.json");
        when(metadata.getContentType()).thenReturn("application/json");
        when(metadata.getArtifactType()).thenReturn("VERIFICATION_BUNDLE");
        when(metadata.getDisplayName()).thenReturn("evidence.json");
        when(metadata.getSizeBytes()).thenReturn(size);
        when(metadata.getSha256()).thenReturn(digest);
        when(metadata.getRetentionClass()).thenReturn("EVIDENCE");
        when(metadata.getCreatedAt()).thenReturn("2026-09-29T12:00:00Z");
        when(metadata.getRetainUntil()).thenReturn("2026-10-29T12:00:00Z");
        when(artifacts.findByRunIdOrderByCreatedAtAsc("run-1")).thenReturn(java.util.List.of(metadata));
        when(store.getVerified("org-1/run-1/task-1/lease-1/evidence.json", digest, 1024)).thenReturn(storedBytes);
    }

    private static Map<String, byte[]> zipEntries(byte[] archive) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                entries.put(entry.getName(), zip.readAllBytes());
        }
        return entries;
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
