package io.forgeloop.control.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** A short-lived, single-owner authorization to execute exactly one task. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "task_id"))
public class TaskLease {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @OneToOne(optional = false) @JoinColumn(name = "task_id") private DeliveryTask task;
    @ManyToOne(optional = false) private Runner runner;
    @Column(nullable = false, unique = true) private String nonceHash;
    @Column(nullable = false) private Instant expiresAt;
    private Instant acknowledgedAt;
    private Instant completedAt;

    protected TaskLease() { }
    public TaskLease(DeliveryTask task, Runner runner, String nonceHash, Instant expiresAt) {
        this.task = task; this.runner = runner; this.nonceHash = nonceHash; this.expiresAt = expiresAt;
    }
    public boolean active() { return completedAt == null && Instant.now().isBefore(expiresAt); }
    public boolean belongsTo(String runnerId) { return runner.getId().equals(runnerId); }
    public boolean matchesNonceHash(String hash) {
        return java.security.MessageDigest.isEqual(nonceHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII), hash.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
    public void acknowledge() { if (!active()) throw new IllegalStateException("Lease is expired"); acknowledgedAt = Instant.now(); }
    public void complete(boolean passed) {
        if (!active() || acknowledgedAt == null) throw new IllegalStateException("Lease must be active and acknowledged before completion");
        task.transition(passed ? TaskState.VERIFIED : TaskState.RETRYABLE_FAILURE); completedAt = Instant.now();
    }
    /** Completes code generation without treating an agent-authored patch as verification evidence. */
    public void completeChangeReady() {
        if (!active() || acknowledgedAt == null) throw new IllegalStateException("Lease must be active and acknowledged before completion");
        task.transition(TaskState.CHANGE_READY); completedAt = Instant.now();
    }
    /** Requeues expired work; the caller removes this lease so the task can be safely re-claimed. */
    public void recover() {
        if (completedAt != null || Instant.now().isBefore(expiresAt)) throw new IllegalStateException("Only expired incomplete leases can be recovered");
        task.transition(TaskState.REPAIR_QUEUED);
    }
    public String getId() { return id; } public String getTaskId() { return task.getId(); }
    public String getRunnerId() { return runner.getId(); } public String getExpiresAt() { return expiresAt.toString(); }
    public boolean isAcknowledged() { return acknowledgedAt != null; } public boolean isCompleted() { return completedAt != null; }
}
