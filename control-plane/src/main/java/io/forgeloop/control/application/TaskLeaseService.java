package io.forgeloop.control.application;

import io.forgeloop.control.domain.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates single-owner task leases and evidence attached to an acknowledged lease. */
@Service
public class TaskLeaseService {
    private final DeliveryTaskRepository tasks;
    private final RunnerRepository runners;
    private final TaskLeaseRepository leases;
    private final VerificationEvidenceRepository evidence;
    private final ProviderAttemptRepository providerAttempts;
    private final RepairPackageRepository repairPackages;
    private final SecureRandom random = new SecureRandom();

    public TaskLeaseService(DeliveryTaskRepository tasks, RunnerRepository runners, TaskLeaseRepository leases,
                            VerificationEvidenceRepository evidence, ProviderAttemptRepository providerAttempts,
                            RepairPackageRepository repairPackages) {
        this.tasks = tasks; this.runners = runners; this.leases = leases; this.evidence = evidence;
        this.providerAttempts = providerAttempts; this.repairPackages = repairPackages;
    }

    @Transactional public LeaseGrant claim(String taskId, String runnerId) {
        DeliveryTask discovered = tasks.findById(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found"));
        DeliveryTask task = tasks.findAllForUpdateByRunId(discovered.getRun().getId()).stream()
                .filter(candidate -> java.util.Objects.equals(candidate.getId(), discovered.getId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        Runner runner = runners.findById(runnerId).orElseThrow(() -> new IllegalArgumentException("Runner not found"));
        if (!runner.isEnabled()) throw new IllegalStateException("Runner is disabled");
        if (!runner.hasCapability(task.getRequiredCapability())) throw new IllegalStateException("Runner lacks the task capability");
        if (task.getState() != TaskState.PENDING && task.getState() != TaskState.REPAIR_QUEUED) throw new IllegalStateException("Task is not claimable");
        if (!task.dependenciesSatisfied()) throw new IllegalStateException("Task dependencies are not complete");
        if (!task.hasBudgetRemaining()) throw new IllegalStateException("Task budget is exhausted");
        if (!task.getRun().hasBudgetRemaining()) throw new IllegalStateException("Run budget is exhausted");
        boolean conflict = tasks.findByRun_Id(task.getRun().getId()).stream()
                .filter(other -> other.getState() == TaskState.LEASED || other.getState() == TaskState.PREPARING || other.getState() == TaskState.RUNNING)
                .anyMatch(task::pathConflictsWith);
        if (conflict) throw new IllegalStateException("Task path ownership conflicts with active work");
        TaskLease existing = leases.findByTask_Id(taskId).orElse(null);
        if (existing != null && existing.active()) throw new IllegalStateException("Task already has an active lease");
        String nonce = secret();
        TaskLease lease = leases.save(new TaskLease(task, runner, hash(nonce), Instant.now().plus(Duration.ofMinutes(10))));
        task.transition(TaskState.LEASED);
        if (!"PLANNER".equals(task.getRole())) task.getRun().startExecution();
        return new LeaseGrant(lease, nonce);
    }

    @Transactional public TaskLease acknowledge(String leaseId, String runnerId, String nonce) {
        TaskLease lease = validatedLease(leaseId, runnerId, nonce); lease.acknowledge(); return lease;
    }

    @Transactional public TaskLease complete(String leaseId, String runnerId, String nonce, boolean passed) {
        TaskLease lease = validatedLease(leaseId, runnerId, nonce);
        DeliveryTask task = tasks.findById(lease.getTaskId()).orElseThrow(() -> new IllegalArgumentException("Task not found"));
        lease.complete(passed);
        if (!passed) {
            String category = providerAttempts.findFirstByTask_IdOrderByRecordedAtDesc(task.getId())
                    .map(ProviderAttempt::getCategory).orElse("VERIFICATION_FAILED");
            String digest = evidence.findFirstByTask_IdOrderByRecordedAtDesc(task.getId())
                    .map(VerificationEvidence::getDigest).orElse(null);
            repairPackages.save(new RepairPackage(task, category, digest));
        }
        return lease;
    }

    @Transactional public TaskLease completeProviderWork(String leaseId, String runnerId, String nonce, String changeSha) {
        TaskLease lease = validatedLease(leaseId, runnerId, nonce); lease.completeChangeReady(changeSha); return lease;
    }

    @Transactional public TaskLease completeIntegration(String leaseId, String runnerId, String nonce, String integratedSha) {
        TaskLease lease = validatedLease(leaseId, runnerId, nonce); lease.completeIntegration(integratedSha); return lease;
    }

    @Transactional public VerificationEvidence recordEvidence(String leaseId, String runnerId, String nonce,
                                                                  VerificationEvidenceSubmission submission) {
        TaskLease lease = validatedLease(leaseId, runnerId, nonce);
        if (!lease.active() || !lease.isAcknowledged()) throw new IllegalStateException("Evidence requires an active acknowledged lease");
        DeliveryTask task = tasks.findById(lease.getTaskId()).orElseThrow(() -> new IllegalArgumentException("Task not found"));
        Runner runner = runners.findById(runnerId).orElseThrow(() -> new IllegalArgumentException("Runner not found"));
        VerificationGate gate = task.getVerificationGate();
        if (gate == null || submission.gate() == null || !gate.matches(submission.gate())) throw new IllegalArgumentException("Evidence is not bound to this verification task");
        VerificationPolicySpec policy = gate.toSpec();
        if (!policy.kind().equals(submission.kind()) || !policy.imageDigest().equals(submission.image()) || !policy.command().equals(submission.command())) throw new IllegalArgumentException("Evidence metadata does not match the run policy snapshot");
        EvidenceSecretPolicy.requireRedacted(submission.output());
        VerificationEvidence candidate = new VerificationEvidence(task, runner, submission.kind(), submission.gate(), submission.image(), submission.command(),
                submission.exitCode(), submission.timedOut(), submission.output(), submission.startedAt(), submission.finishedAt(), submission.artifactReference(), submission.outputDigest(), submission.bundleDigest());
        VerificationEvidence recorded = evidence.findByDigest(candidate.getDigest()).orElseGet(() -> evidence.save(candidate));
        task.getRun().recordGate(submission.gate(), !submission.timedOut() && submission.exitCode() == 0, submission.timedOut());
        return recorded;
    }

    /** Accepts idempotent, redacted provider telemetry only from the active worker that owns the task lease. */
    @Transactional public ProviderAttempt recordProviderAttempt(String leaseId, String runnerId, String nonce,
                                                                 ProviderAttemptSubmission submission) {
        TaskLease lease = validatedLease(leaseId, runnerId, nonce);
        if (!lease.active() || !lease.isAcknowledged()) throw new IllegalStateException("Provider evidence requires an active acknowledged lease");
        DeliveryTask task = tasks.findById(lease.getTaskId()).orElseThrow(() -> new IllegalArgumentException("Task not found"));
        Runner runner = runners.findById(runnerId).orElseThrow(() -> new IllegalArgumentException("Runner not found"));
        ProviderAttempt recorded = providerAttempts.findByTask_IdAndRequestIdDigest(task.getId(), submission.requestIdDigest()).orElseGet(() ->
                providerAttempts.save(new ProviderAttempt(task, runner, submission.provider(), submission.model(), submission.requestIdDigest(),
                        submission.inputTokens(), submission.outputTokens(), submission.attemptCount(), submission.estimatedCostMicros(),
                        submission.costKnown(), submission.outcome(), submission.retryable(), submission.category())));
        long taskSpent = providerAttempts.sumKnownCostByTaskId(task.getId());
        long runSpent = providerAttempts.sumKnownCostByRunId(task.getRun().getId());
        long runBudget = Math.round(task.getRun().getBudgetUsd() * 1_000_000d);
        if ((task.getBudgetMicros() > 0 && taskSpent >= task.getBudgetMicros()) || runSpent >= runBudget) task.getRun().block();
        return recorded;
    }

    /** Returns the active task identity only after validating the runner-bound lease credentials. */
    @Transactional public String requireActiveTaskId(String leaseId, String runnerId, String nonce) {
        TaskLease lease = validatedLease(leaseId, runnerId, nonce);
        if (!lease.active() || !lease.isAcknowledged()) throw new IllegalStateException("Operation requires an active acknowledged lease");
        return lease.getTaskId();
    }

    private TaskLease validatedLease(String leaseId, String runnerId, String nonce) {
        TaskLease lease = leases.findById(leaseId).orElseThrow(() -> new IllegalArgumentException("Lease not found"));
        if (!lease.belongsTo(runnerId) || !lease.matchesNonceHash(hash(nonce))) throw new IllegalArgumentException("Lease credentials are invalid");
        return lease;
    }

    private String secret() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return HexFormat.of().formatHex(bytes); }
    private String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
