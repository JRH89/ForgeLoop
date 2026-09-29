package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.forgeloop.control.artifacts.ArtifactStore;
import io.forgeloop.control.domain.ArtifactMetadata;
import io.forgeloop.control.domain.ArtifactMetadataRepository;
import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.DeliveryTaskRepository;
import io.forgeloop.control.domain.FeatureRun;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ArtifactUploadServiceTest {
    private final TaskLeaseService leases = mock(TaskLeaseService.class);
    private final DeliveryTaskRepository tasks = mock(DeliveryTaskRepository.class);
    private final ArtifactMetadataRepository metadata = mock(ArtifactMetadataRepository.class);
    private final ArtifactStore store = mock(ArtifactStore.class);
    private final ArtifactUploadService service = new ArtifactUploadService(leases, tasks, metadata, store, 1024, 30, 365, 128);

    @Test void storesVerifiedArtifactAndTenantBoundMetadata() throws Exception {
        byte[] content = "{\"passed\":true}".getBytes(StandardCharsets.UTF_8);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        DeliveryTask task = mock(DeliveryTask.class);
        FeatureRun run = mock(FeatureRun.class);
        when(leases.requireActiveTaskId("lease", "runner", "nonce")).thenReturn("task");
        when(tasks.findById("task")).thenReturn(Optional.of(task));
        when(task.getRun()).thenReturn(run);
        when(task.getId()).thenReturn("task");
        when(run.getOrganizationId()).thenReturn("org");
        when(run.getId()).thenReturn("run");
        when(store.putVerified("org/run/task/lease/evidence.json", content, "application/json", digest))
                .thenReturn(new ArtifactStore.StoredObject(content.length, digest));
        when(metadata.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ArtifactMetadata result = service.upload("lease", "runner", "nonce", "application/json", "VERIFICATION_BUNDLE", "evidence.json", digest, content);

        assertEquals("artifact://org/run/task/lease/evidence.json", result.getStorageReference());
        assertEquals(digest, result.getSha256());
        verify(metadata).save(any(ArtifactMetadata.class));
    }

    @Test void returnsSameMetadataForAnIdenticalLeaseRetry() throws Exception {
        byte[] content = "{}".getBytes(StandardCharsets.UTF_8);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        ArtifactMetadata existing = mock(ArtifactMetadata.class);
        when(leases.requireActiveTaskId("lease", "runner", "nonce")).thenReturn("task");
        when(metadata.findByLeaseIdAndDisplayName("lease", "evidence.json")).thenReturn(Optional.of(existing));
        when(existing.getSha256()).thenReturn(digest);
        when(existing.getSizeBytes()).thenReturn((long) content.length);

        assertSame(existing, service.upload("lease", "runner", "nonce", "application/json", "VERIFICATION_BUNDLE", "evidence.json", digest, content));
        verify(store, never()).putVerified(any(), any(), any(), any());
    }

    @Test void rejectsChecksumMismatchBeforeWriting() {
        byte[] content = "{}".getBytes(StandardCharsets.UTF_8);
        when(leases.requireActiveTaskId("lease", "runner", "nonce")).thenReturn("task");

        assertThrows(IllegalArgumentException.class,
                () -> service.upload("lease", "runner", "nonce", "application/json", "VERIFICATION_BUNDLE", "evidence.json", "0".repeat(64), content));
        verify(store, never()).putVerified(any(), any(), any(), any());
    }

    @Test void rejectsAClaimedScreenshotWithoutAPngSignature() throws Exception {
        byte[] content = "not-png".getBytes(StandardCharsets.UTF_8);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        when(leases.requireActiveTaskId("lease", "runner", "nonce")).thenReturn("task");

        assertThrows(IllegalArgumentException.class, () -> service.upload("lease", "runner", "nonce",
                "image/png", "SCREENSHOT", "screen.png", digest, content));
        verify(store, never()).putVerified(any(), any(), any(), any());
    }

    @Test void acceptsJournalOnlyForOptedInRunAndUsesRecordRetentionClass() throws Exception {
        DeliveryTask task = taskForRun(true);
        when(leases.requireActiveTaskId("lease", "runner", "nonce")).thenReturn("task");
        when(tasks.findById("task")).thenReturn(Optional.of(task));
        when(metadata.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        byte[] content = gzip("{\"seq\":1,\"leaseId\":\"lease\",\"type\":\"WORKER_STARTED\",\"input\":\"apiKey: example\"}\n");
        String digest = digest(content);
        when(store.putVerified("org/run/task/lease/journal-1-1.jsonl.gz", content, "application/gzip", digest))
                .thenReturn(new ArtifactStore.StoredObject(content.length, digest));

        ArtifactMetadata uploaded = service.upload("lease", "runner", "nonce", "application/gzip", "RUN_JOURNAL",
                "journal-1-1.jsonl.gz", digest, content);

        assertEquals("RUN_RECORD", uploaded.getRetentionClass());
        assertEquals("application/gzip", uploaded.getContentType());
        assertEquals("artifact://org/run/task/lease/journal-1-1.jsonl.gz", uploaded.getStorageReference());
        verify(store).putVerified(any(), any(), any(), any());
    }

    @Test void rejectsJournalForRecordOffRunBeforeWriting() throws Exception {
        DeliveryTask task = taskForRun(false);
        when(leases.requireActiveTaskId("lease", "runner", "nonce")).thenReturn("task");
        when(tasks.findById("task")).thenReturn(Optional.of(task));
        byte[] content = gzip("{\"seq\":1,\"leaseId\":\"lease\",\"type\":\"WORKER_STARTED\"}\n");

        assertThrows(IllegalArgumentException.class, () -> service.upload("lease", "runner", "nonce", "application/gzip",
                "RUN_JOURNAL", "journal-1-1.jsonl.gz", digest(content), content));
        verify(store, never()).putVerified(any(), any(), any(), any());
    }

    @Test void rejectsSecretsMalformedJsonAndExpandedOversizeJournals() throws Exception {
        DeliveryTask task = taskForRun(true);
        when(leases.requireActiveTaskId("lease", "runner", "nonce")).thenReturn("task");
        when(tasks.findById("task")).thenReturn(Optional.of(task));

        assertJournalRejected(gzip("{\"seq\":1,\"leaseId\":\"lease\",\"type\":\"CALL_COMPLETED\",\"response\":\"ghp_abcdefghijklmnopqrstuvwxyz123456\"}\n"),
                "possible raw secret");
        assertJournalRejected(gzip("not-json\n"), "invalid JSON line");
        assertJournalRejected(gzip("{\"seq\":1,\"leaseId\":\"lease\",\"type\":\"WORKER_STARTED\"} {\"extra\":true}\n"), "invalid JSON line");
        byte[] random = new byte[512];
        new java.security.SecureRandom().nextBytes(random);
        String expanded = "{\"seq\":1,\"leaseId\":\"lease\",\"type\":\"CALL_COMPLETED\",\"content\":\"" + java.util.Base64.getEncoder().encodeToString(random) + "\"}\n";
        assertJournalRejected(gzip(expanded), "expanded-size bound");
        assertJournalRejected(gzip("{\"seq\":1,\"leaseId\":\"other-lease\",\"type\":\"WORKER_STARTED\"}\n"), "invalid JSON line");
        verify(store, never()).putVerified(any(), any(), any(), any());
    }

    private void assertJournalRejected(byte[] content, String message) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.upload("lease", "runner", "nonce",
                "application/gzip", "RUN_JOURNAL", "journal-1-1.jsonl.gz", digest(content), content));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains(message));
    }

    private DeliveryTask taskForRun(boolean enabled) {
        DeliveryTask task = mock(DeliveryTask.class);
        FeatureRun run = mock(FeatureRun.class);
        when(task.getRun()).thenReturn(run);
        when(task.getId()).thenReturn("task");
        when(run.getOrganizationId()).thenReturn("org");
        when(run.getId()).thenReturn("run");
        when(run.isRunRecordEnabled()).thenReturn(enabled);
        return task;
    }

    private static byte[] gzip(String value) throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(bytes)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}
