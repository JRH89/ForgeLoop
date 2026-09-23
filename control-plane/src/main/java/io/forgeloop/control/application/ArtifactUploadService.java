package io.forgeloop.control.application;

import io.forgeloop.control.artifacts.ArtifactStore;
import io.forgeloop.control.domain.ArtifactMetadata;
import io.forgeloop.control.domain.ArtifactMetadataRepository;
import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.DeliveryTaskRepository;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accepts only bounded, checksummed evidence from the runner that owns an active acknowledged lease. */
@Service
public class ArtifactUploadService {
    private final TaskLeaseService leases; private final DeliveryTaskRepository tasks;
    private final ArtifactMetadataRepository metadata; private final ArtifactStore store;
    private final long maxBytes; private final int retentionDays;
    public ArtifactUploadService(TaskLeaseService leases, DeliveryTaskRepository tasks, ArtifactMetadataRepository metadata,
                                 ArtifactStore store, @Value("${forgeloop.artifacts.max-bytes:1048576}") long maxBytes,
                                 @Value("${forgeloop.artifacts.retention-days:30}") int retentionDays) {
        if (maxBytes < 1 || maxBytes > 10 * 1024 * 1024) throw new IllegalArgumentException("Artifact size limit is invalid");
        if (retentionDays < 1 || retentionDays > 3650) throw new IllegalArgumentException("Artifact retention is invalid");
        this.leases=leases;this.tasks=tasks;this.metadata=metadata;this.store=store;this.maxBytes=maxBytes;this.retentionDays=retentionDays;
    }
    @Transactional public ArtifactMetadata upload(String leaseId, String runnerId, String nonce, String contentType,
                                                    String claimedSha256, byte[] content) {
        String taskId = leases.requireActiveTaskId(leaseId, runnerId, nonce);
        if (content == null || content.length == 0 || content.length > maxBytes) throw new IllegalArgumentException("Artifact size is outside policy bounds");
        if (contentType == null || !contentType.matches("application/json(?:; charset=utf-8)?")) throw new IllegalArgumentException("Artifact content type is not allowed");
        String actual = digest(content);
        if (claimedSha256 == null || !claimedSha256.matches("[0-9a-f]{64}") || !actual.equals(claimedSha256)) throw new IllegalArgumentException("Artifact checksum mismatch");
        ArtifactMetadata existing = metadata.findByLeaseId(leaseId).orElse(null);
        if (existing != null) {
            if (!existing.getSha256().equals(claimedSha256) || existing.getSizeBytes() != content.length) throw new IllegalStateException("Lease artifact was already recorded with different content");
            return existing;
        }
        DeliveryTask task = tasks.findById(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found"));
        String key = task.getRun().getOrganizationId() + "/" + task.getRun().getId() + "/" + taskId + "/" + leaseId + ".json";
        ArtifactStore.StoredObject stored = store.putVerified(key, content, contentType, actual);
        String reference = "artifact://" + key;
        return metadata.save(new ArtifactMetadata(task, leaseId, reference, contentType, stored.sizeBytes(), stored.sha256(), Instant.now().plus(Duration.ofDays(retentionDays))));
    }
    private static String digest(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (Exception failure) { throw new IllegalStateException("SHA-256 is unavailable", failure); }
    }
}
