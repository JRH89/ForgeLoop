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
    private final SecureRandom random = new SecureRandom();

    public TaskLeaseService(DeliveryTaskRepository tasks, RunnerRepository runners, TaskLeaseRepository leases,
                            VerificationEvidenceRepository evidence) {
        this.tasks = tasks; this.runners = runners; this.leases = leases; this.evidence = evidence;
    }

    @Transactional public LeaseGrant claim(String taskId, String runnerId) {
        DeliveryTask task = tasks.findById(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found"));
        Runner runner = runners.findById(runnerId).orElseThrow(() -> new IllegalArgumentException("Runner not found"));
        if (!runner.isEnabled()) throw new IllegalStateException("Runner is disabled");
        if (!runner.hasCapability(task.getRequiredCapability())) throw new IllegalStateException("Runner lacks the task capability");
        if (task.getState() != TaskState.PENDING && task.getState() != TaskState.REPAIR_QUEUED) throw new IllegalStateException("Task is not claimable");
        TaskLease existing = leases.findByTask_Id(taskId).orElse(null);
        if (existing != null && existing.active()) throw new IllegalStateException("Task already has an active lease");
        String nonce = secret();
        TaskLease lease = leases.save(new TaskLease(task, runner, hash(nonce), Instant.now().plus(Duration.ofMinutes(10))));
        task.transition(TaskState.LEASED);
        return new LeaseGrant(lease, nonce);
    }

    @Transactional public TaskLease acknowledge(String leaseId, String runnerId, String nonce) {
        TaskLease lease = validatedLease(leaseId, runnerId, nonce); lease.acknowledge(); return lease;
    }

    @Transactional public TaskLease complete(String leaseId, String runnerId, String nonce, boolean passed) {
        TaskLease lease = validatedLease(leaseId, runnerId, nonce); lease.complete(passed); return lease;
    }

    @Transactional public VerificationEvidence recordEvidence(String leaseId, String runnerId, String nonce,
                                                                  VerificationEvidenceSubmission submission) {
        TaskLease lease = validatedLease(leaseId, runnerId, nonce);
        if (!lease.active() || !lease.isAcknowledged()) throw new IllegalStateException("Evidence requires an active acknowledged lease");
        DeliveryTask task = tasks.findById(lease.getTaskId()).orElseThrow(() -> new IllegalArgumentException("Task not found"));
        Runner runner = runners.findById(runnerId).orElseThrow(() -> new IllegalArgumentException("Runner not found"));
        VerificationEvidence recorded = evidence.save(new VerificationEvidence(task, runner, submission.kind(), submission.gate(), submission.image(), submission.command(),
                submission.exitCode(), submission.timedOut(), submission.output()));
        if (submission.gate() != null) task.getRun().recordGate(submission.gate(), !submission.timedOut() && submission.exitCode() == 0);
        return recorded;
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
