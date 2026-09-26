package io.forgeloop.control.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QueuePolicyTest {
    @Test void archiveRequiresTerminalWorkAndIsReversible() {
        var run = new FeatureRun("org", "owner/repo", "issue-1", "title", "spec", 10, "GENERIC", 1);
        assertThrows(IllegalStateException.class, () -> run.setArchived(true));
        run.cancel(); run.setArchived(true);
        assertTrue(run.isArchived());
        run.setArchived(false);
        assertFalse(run.isArchived());
        assertEquals(RunState.CANCELLED, run.getState());
    }
    @Test void assigneeGateIsOptionalCaseInsensitiveAndValidated() {
        var repository = new RepositoryConnection("org", "owner/repo", 1, "main", "forgeloop", "GENERIC", List.of("unit"), 10);
        assertTrue(repository.acceptsAssignees(List.of()));
        repository.configureRequiredAssignee(" Worker ");
        assertFalse(repository.acceptsAssignees(List.of()));
        assertFalse(repository.acceptsAssignees(List.of("someone-else")));
        assertTrue(repository.acceptsAssignees(List.of("worker")));
        assertThrows(IllegalArgumentException.class, () -> repository.configureRequiredAssignee("@worker"));
        repository.configureRequiredAssignee("");
        assertTrue(repository.acceptsAssignees(List.of()));
    }
    @Test void confirmedMergeCanArchiveLegacyDeliveryWithoutChangingItsState() {
        var run = new FeatureRun("org", "owner/repo", "issue-1", "title", "spec", 10, "GENERIC", 1);
        // A fixture without required gates models a previously verified delivery.
        run.evaluateReviewReadiness();
        assertEquals(RunState.READY_FOR_REVIEW,run.getState());
        assertThrows(IllegalStateException.class,()->run.setArchived(true));
        run.setArchived(true,true);
        assertTrue(run.isArchived());assertFalse(run.hasBudgetRemaining());
        assertEquals(RunState.READY_FOR_REVIEW,run.getState());
    }
}
