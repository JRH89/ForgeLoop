package io.forgeloop.control.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Tenant-bound immutable evidence-object metadata; artifact bytes live only in the configured store. */
@Entity
@Table(name = "artifact_metadata")
public class ArtifactMetadata {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @Column(nullable = false) private String organizationId;
    @Column(nullable = false) private String runId;
    @Column(nullable = false) private String taskId;
    @Column(nullable = false, unique = true) private String leaseId;
    @Column(nullable = false, unique = true, length = 1000) private String storageReference;
    @Column(nullable = false) private String contentType;
    @Column(nullable = false) private long sizeBytes;
    @Column(nullable = false, length = 64) private String sha256;
    @Column(nullable = false) private String retentionClass;
    @Column(nullable = false) private Instant retainUntil;
    @Column(nullable = false) private Instant createdAt;

    protected ArtifactMetadata() { }
    public ArtifactMetadata(DeliveryTask task, String leaseId, String storageReference, String contentType,
                            long sizeBytes, String sha256, Instant retainUntil) {
        this.organizationId = task.getRun().getOrganizationId(); this.runId = task.getRun().getId(); this.taskId = task.getId();
        this.leaseId = leaseId; this.storageReference = storageReference; this.contentType = contentType;
        this.sizeBytes = sizeBytes; this.sha256 = sha256; this.retentionClass = "EVIDENCE";
        this.retainUntil = retainUntil; this.createdAt = Instant.now();
    }
    public String getId() { return id; } public String getOrganizationId() { return organizationId; }
    public String getRunId() { return runId; } public String getTaskId() { return taskId; }
    public String getLeaseId() { return leaseId; } public String getStorageReference() { return storageReference; }
    public String getContentType() { return contentType; } public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; } public String getRetentionClass() { return retentionClass; }
    public String getRetainUntil() { return retainUntil.toString(); } public String getCreatedAt() { return createdAt.toString(); }
}
