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
    private final ArtifactUploadService service = new ArtifactUploadService(leases, tasks, metadata, store, 1024, 30);

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
}
