package io.forgeloop.control.application;

import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.DeliveryTaskRepository;
import io.forgeloop.control.domain.ReviewEvidence;
import io.forgeloop.control.domain.ReviewEvidenceRepository;
import io.forgeloop.control.domain.Runner;
import io.forgeloop.control.domain.RunnerRepository;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates and persists criterion-complete review evidence under an active runner lease. */
@Service
public class ReviewEvidenceService {
    private final TaskLeaseService leases;
    private final DeliveryTaskRepository tasks;
    private final RunnerRepository runners;
    private final ReviewEvidenceRepository reviews;
    public ReviewEvidenceService(TaskLeaseService leases, DeliveryTaskRepository tasks, RunnerRepository runners, ReviewEvidenceRepository reviews) { this.leases=leases;this.tasks=tasks;this.runners=runners;this.reviews=reviews; }

    @Transactional
    public ReviewEvidence record(String leaseId, String runnerId, String nonce, ReviewEvidenceSubmission submission) {
        DeliveryTask task = tasks.findById(leases.requireActiveTaskId(leaseId, runnerId, nonce)).orElseThrow(() -> new IllegalArgumentException("Task not found"));
        if (!"REVIEW".equals(task.getRole())) throw new IllegalArgumentException("Review evidence requires a review task");
        if (submission == null || submission.summary() == null || submission.summary().isBlank() || submission.summary().length() > 2000 || submission.criteria() == null) throw new IllegalArgumentException("Review evidence is incomplete");
        EvidenceSecretPolicy.requireRedacted(submission.summary());
        List<String> expected = task.getRun().getCriteria().stream().map(item -> item.getStatement()).toList();
        List<String> submitted = submission.criteria().stream().map(ReviewCriterionSubmission::statement).toList();
        if (submitted.size() != expected.size() || new HashSet<>(submitted).size() != submitted.size() || !new HashSet<>(submitted).equals(new HashSet<>(expected))) throw new IllegalArgumentException("Review evidence must assess every acceptance criterion exactly once");
        submission.criteria().forEach(item -> { if (!List.of("PASS", "FAIL").contains(item.status()) || item.evidence() == null || item.evidence().isBlank() || item.evidence().length() > 4000) throw new IllegalArgumentException("Review criterion evidence is invalid"); EvidenceSecretPolicy.requireRedacted(item.evidence()); });
        boolean allPassed = submission.criteria().stream().allMatch(item -> "PASS".equals(item.status()));
        if (submission.approved() != allPassed) throw new IllegalArgumentException("Review approval must match criterion outcomes");
        Runner runner = runners.findById(runnerId).orElseThrow(() -> new IllegalArgumentException("Runner not found"));
        ReviewEvidence candidate = new ReviewEvidence(task, runner, submission.approved(), submission.summary(), submission.criteria());
        return reviews.findByDigest(candidate.getDigest()).orElseGet(() -> reviews.save(candidate));
    }
}
