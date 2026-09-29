package io.forgeloop.control.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** A short-lived, single-owner authorization to execute exactly one task. */
@Entity
public class TaskLease {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @ManyToOne(optional = false) @JoinColumn(name = "task_id") private DeliveryTask task;
    @ManyToOne(optional = false) private Runner runner;
    @Column(nullable = false, unique = true) private String nonceHash;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false) private Instant claimedAt;
    private Instant acknowledgedAt;
    private Instant completedAt;
    @Column(nullable = false)
    private long reservedMicros;
    @Column(length = 64) private String runnerRevision;
    @Column(length = 64) private String runnerJarSha256;
    @Column(columnDefinition = "text") private String inputRefs;
    @Column(length = 64) private String resultSha;
    @Enumerated(EnumType.STRING) @Column(length = 32) private AttemptOutcome outcome;
    @Column(length = 80) private String outcomeCategory;

    protected TaskLease() { }
    public TaskLease(DeliveryTask task, Runner runner, String nonceHash, Instant expiresAt) {
        this.task = task; this.runner = runner; this.nonceHash = nonceHash; this.expiresAt = expiresAt; this.claimedAt = Instant.now();
    }
    public boolean active() { return completedAt == null && Instant.now().isBefore(expiresAt); }
    public boolean belongsTo(String runnerId) { return runner.getId().equals(runnerId); }
    public boolean matchesNonceHash(String hash) {
        return java.security.MessageDigest.isEqual(nonceHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII), hash.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
    public void acknowledge() { acknowledge(null, null); }
    /** Pins the runner build on first acknowledgement; old runner clients may omit both optional values. */
    public void acknowledge(String revision, String jarSha256) {
        if (!active()) throw new IllegalStateException("Lease is expired");
        if ((revision == null) != (jarSha256 == null)) throw new IllegalArgumentException("Runner build is invalid");
        if (revision != null && !("unknown".equals(revision) || revision.matches("[0-9a-f]{7,64}")))
            throw new IllegalArgumentException("Runner build is invalid");
        if (jarSha256 != null && !("unpackaged".equals(jarSha256) || jarSha256.matches("[0-9a-f]{64}")))
            throw new IllegalArgumentException("Runner build is invalid");
        if (acknowledgedAt != null) {
            if (!java.util.Objects.equals(runnerRevision, revision) || !java.util.Objects.equals(runnerJarSha256, jarSha256))
                throw new IllegalStateException("Runner build identity is already pinned");
            return;
        }
        runnerRevision = revision;
        runnerJarSha256 = jarSha256;
        acknowledgedAt = Instant.now();
    }
    /** Captures the immutable refs before any runner action can change the repository state. */
    public void captureInputRefs(String canonicalJson) {
        if (canonicalJson == null || canonicalJson.isBlank() || canonicalJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65_536)
            throw new IllegalArgumentException("Lease input refs are invalid");
        if (inputRefs != null && !inputRefs.equals(canonicalJson)) throw new IllegalStateException("Lease input refs are already pinned");
        inputRefs = canonicalJson;
    }
    /** Replaces, rather than increments, the active lease's worst-case provider-spend reservation. */
    public void reserve(long micros) {
        if (!active() || acknowledgedAt == null) throw new IllegalStateException("Lease must be active and acknowledged before reserving spend");
        if (micros < 1 || micros > 1_000_000_000_000L) throw new IllegalArgumentException("Spend reservation is invalid");
        reservedMicros = micros;
    }
    /** Releases the reservation after provider usage is persisted or the lease is closed. */
    public void settleReservation() { reservedMicros = 0; }
    private void close(AttemptOutcome attemptOutcome, String category) {
        if (attemptOutcome == null) throw new IllegalArgumentException("Attempt outcome is required");
        if (category == null || !category.matches("[A-Z_]{1,80}")) throw new IllegalArgumentException("Attempt outcome category is invalid");
        completedAt = Instant.now();
        outcome = attemptOutcome;
        outcomeCategory = category;
        settleReservation();
    }
    public void complete(boolean passed) {
        complete(passed, passed ? "COMPLETED" : "EXECUTION_FAILED");
    }
    /** Closes a general attempt with the same category used for its retry package. */
    public void complete(boolean passed, String category) {
        if (!active() || acknowledgedAt == null) throw new IllegalStateException("Lease must be active and acknowledged before completion");
        task.transition(passed ? TaskState.VERIFIED : TaskState.REPAIR_QUEUED);
        if (task.getState() == TaskState.FAILED) task.getRun().block();
        if (passed) task.getRun().evaluateReviewReadiness();
        close(passed ? AttemptOutcome.CLEAN : AttemptOutcome.HARNESS_FAILURE, category);
    }
    /** Closes a failed quality-stage lease before the run atomically materializes its code-repair cycle. */
    public void closeForRepairCycle() {
        closeForRepairCycle("VERIFICATION_FAILED");
    }
    public void closeForRepairCycle(String category) {
        if (!active() || acknowledgedAt == null) throw new IllegalStateException("Lease must be active and acknowledged before completion");
        close(AttemptOutcome.FINDINGS, category);
    }
    /** Check verdict routing performs its own task transitions, then closes this lease atomically. */
    public void closeForTestCheck() {
        closeForTestCheck(AttemptOutcome.CLEAN, "COMPLETED");
    }
    public void closeForTestCheck(AttemptOutcome attemptOutcome, String category) {
        if (!active() || acknowledgedAt == null) throw new IllegalStateException("Lease must be active and acknowledged before completion");
        close(attemptOutcome, category);
    }
    /** Closes an acknowledged lease after the task is held by a server-enforced policy boundary. */
    public void closeForPolicyHold() {
        closeForPolicyHold("POLICY_HOLD");
    }
    public void closeForPolicyHold(String category) {
        if (!active() || acknowledgedAt == null) throw new IllegalStateException("Lease must be active and acknowledged before completion");
        close(AttemptOutcome.STOPPED, category);
    }
    /** Extends only acknowledged loop leases and never beyond the original claim-time budget cap. */
    public void renew(Instant now, Instant maximumExpiry) {
        if (now == null || completedAt != null || acknowledgedAt == null || !now.isBefore(expiresAt))
            throw new IllegalStateException("Lease must be active and acknowledged before renewal");
        if (maximumExpiry == null || !maximumExpiry.isAfter(now)) throw new IllegalArgumentException("Lease renewal limit reached");
        Instant requested = now.plus(java.time.Duration.ofMinutes(10));
        expiresAt = requested.isBefore(maximumExpiry) ? requested : maximumExpiry;
    }
    /** Closes an acknowledged lease after the runner deliberately holds its task for an operator. */
    public void closeForHold() {
        closeForHold("WORKER_DECLINED");
    }
    public void closeForHold(String reason) {
        if (!active() || acknowledgedAt == null) throw new IllegalStateException("Lease must be active and acknowledged before completion");
        close(AttemptOutcome.STOPPED, reason);
    }
    /** Completes code generation without treating an agent-authored patch as verification evidence. */
    public void completeChangeReady(String changeSha) {
        if (!active() || acknowledgedAt == null) throw new IllegalStateException("Lease must be active and acknowledged before completion");
        task.recordChangeSha(changeSha);
        resultSha = changeSha;
        task.transition(TaskState.CHANGE_READY); close(AttemptOutcome.CLEAN, "COMPLETED");
    }
    /** Closes a planner lease after its graph was materialized in the same transaction. */
    public void completePlanning() {
        if (!active() || acknowledgedAt == null || task.getState() != TaskState.VERIFIED) {
            throw new IllegalStateException("Planner lease can close only after an acknowledged, verified plan");
        }
        close(AttemptOutcome.CLEAN, "COMPLETED");
    }
    /** Atomically records the integration head and advances only its declared dependencies. */
    public void completeIntegration(String integratedSha) {
        if (!active() || acknowledgedAt == null) throw new IllegalStateException("Lease must be active and acknowledged before completion");
        task.integrateDependencies();
        task.recordChangeSha(integratedSha);
        resultSha = integratedSha;
        task.transition(TaskState.INTEGRATED);
        close(AttemptOutcome.CLEAN, "COMPLETED");
    }
    /** Requeues expired work; terminal work is merely closed and reports that no repair package is needed. */
    public boolean recover() {
        if (completedAt != null || Instant.now().isBefore(expiresAt)) throw new IllegalStateException("Only expired incomplete leases can be recovered");
        if (java.util.List.of(RunState.COMPLETE, RunState.CANCELLED, RunState.REJECTED, RunState.FAILED).contains(task.getRun().getState())
                || java.util.List.of(TaskState.VERIFIED, TaskState.FAILED, TaskState.HELD).contains(task.getState())) {
            close(AttemptOutcome.STOPPED, "LEASE_CLOSED_AFTER_STOP");
            return false;
        }
        task.transition(TaskState.REPAIR_QUEUED);
        if (task.getState() == TaskState.FAILED) task.getRun().block();
        close(AttemptOutcome.HARNESS_FAILURE, "LEASE_EXPIRED");
        return true;
    }
    public String getId() { return id; } public String getTaskId() { return task.getId(); }
    public long getReservedMicros() { return reservedMicros; }
    public String getRunnerRevision() { return runnerRevision; }
    public String getRunnerJarSha256() { return runnerJarSha256; }
    public String getInputRefs() { return inputRefs; }
    public String getResultSha() { return resultSha; }
    public AttemptOutcome getOutcome() { return outcome; }
    public String getOutcomeCategory() { return outcomeCategory; }
    public DeliveryTask getTask() { return task; }
    public String getRunnerId() { return runner.getId(); } public String getExpiresAt() { return expiresAt.toString(); }
    public Instant getClaimedAt() { return claimedAt; }
    public boolean isAcknowledged() { return acknowledgedAt != null; } public boolean isCompleted() { return completedAt != null; }
}
