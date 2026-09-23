package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.DeliveryTaskRepository;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.ReviewEvidenceRepository;
import io.forgeloop.control.domain.Runner;
import io.forgeloop.control.domain.RunnerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReviewEvidenceServiceTest {
    private final TaskLeaseService leases = mock(TaskLeaseService.class);
    private final DeliveryTaskRepository tasks = mock(DeliveryTaskRepository.class);
    private final RunnerRepository runners = mock(RunnerRepository.class);
    private final ReviewEvidenceRepository reviews = mock(ReviewEvidenceRepository.class);
    private final ReviewEvidenceService service = new ReviewEvidenceService(leases, tasks, runners, reviews);

    @Test
    void recordsACompleteRedactedCriterionAssessment() {
        DeliveryTask task = reviewTask();
        Runner runner = mock(Runner.class);
        when(leases.requireActiveTaskId("lease", "runner", "nonce")).thenReturn("task");
        when(tasks.findById("task")).thenReturn(Optional.of(task));
        when(runners.findById("runner")).thenReturn(Optional.of(runner));
        when(reviews.save(any())).thenAnswer(call -> call.getArgument(0));
        ReviewEvidenceSubmission submission = new ReviewEvidenceSubmission(true, "Requirement is covered", List.of(new ReviewCriterionSubmission("Authorization is enforced", "PASS", "The service checks membership")));

        var recorded = service.record("lease", "runner", "nonce", submission);

        assertEquals(64, recorded.getDigest().length());
        assertEquals("PASS", recorded.getCriteria().getFirst().getStatus());
    }

    @Test
    void rejectsMissingCriteriaAndContradictoryApproval() {
        DeliveryTask task = reviewTask();
        when(leases.requireActiveTaskId("lease", "runner", "nonce")).thenReturn("task");
        when(tasks.findById("task")).thenReturn(Optional.of(task));

        assertThrows(IllegalArgumentException.class, () -> service.record("lease", "runner", "nonce", new ReviewEvidenceSubmission(true, "Looks good", List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.record("lease", "runner", "nonce", new ReviewEvidenceSubmission(true, "Looks good", List.of(new ReviewCriterionSubmission("Authorization is enforced", "FAIL", "Guard is missing")))));
    }

    private static DeliveryTask reviewTask() {
        FeatureRun run = new FeatureRun("acme/ticketly", "issue-1", "Feature", "Secure it", 10, "GENERIC", 1);
        run.addCriterion("Authorization is enforced");
        run.addIndependentReviewTask();
        return run.getTasks().getFirst();
    }
}
