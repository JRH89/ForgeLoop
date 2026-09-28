package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.FeatureRunRepository;
import io.forgeloop.control.domain.RunState;
import io.forgeloop.control.domain.TaskLeaseRepository;
import io.forgeloop.control.security.OperatorContext;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunDeletionServiceTest {
    private final FeatureRunRepository runs = mock(FeatureRunRepository.class);
    private final TaskLeaseRepository leases = mock(TaskLeaseRepository.class);
    private final RunPurgeRepository purge = mock(RunPurgeRepository.class);
    private final OperatorContext operators = mock(OperatorContext.class);
    private final AuditLedgerService audit = mock(AuditLedgerService.class);
    private final RunDeletionService service = new RunDeletionService(runs, leases, purge, operators, audit);

    @Test void deletesOnlyArchivedTerminalRunsAndRetainsAnAuditRecord() {
        FeatureRun run = terminalRun();
        when(runs.findByIdForUpdate("run-1")).thenReturn(Optional.of(run));
        when(leases.countByTask_Run_IdAndCompletedAtIsNullAndExpiresAtAfter(eq("run-1"), any(Instant.class))).thenReturn(0L);
        when(purge.delete("run-1")).thenReturn(true);

        assertTrue(service.delete("run-1"));

        var ordered = inOrder(operators, runs, leases, audit, purge);
        ordered.verify(operators).requireOperator();
        ordered.verify(runs).findByIdForUpdate("run-1");
        ordered.verify(operators).requireOrganization("org-1");
        ordered.verify(leases).countByTask_Run_IdAndCompletedAtIsNullAndExpiresAtAfter(eq("run-1"), any(Instant.class));
        ordered.verify(audit).record("RUN_DELETED", "FEATURE_RUN", "run-1", RunState.CANCELLED.name());
        ordered.verify(purge).delete("run-1");
    }

    @Test void refusesUnarchivedRuns() {
        FeatureRun run = new FeatureRun("org-1", "owner/repo", "issue-1", "title", "spec", 10, "GENERIC", 1);
        run.cancel();
        when(runs.findByIdForUpdate("run-1")).thenReturn(Optional.of(run));

        assertThrows(IllegalStateException.class, () -> service.delete("run-1"));
        verifyNoInteractions(leases, purge, audit);
    }

    @Test void refusesRunsWithUnexpiredRunnerLeases() {
        FeatureRun run = terminalRun();
        when(runs.findByIdForUpdate("run-1")).thenReturn(Optional.of(run));
        when(leases.countByTask_Run_IdAndCompletedAtIsNullAndExpiresAtAfter(eq("run-1"), any(Instant.class))).thenReturn(1L);

        assertThrows(IllegalStateException.class, () -> service.delete("run-1"));
        verifyNoInteractions(purge, audit);
    }

    @Test void viewerCannotReachRunLookupOrDelete() {
        doThrow(new org.springframework.security.access.AccessDeniedException("Denied")).when(operators).requireOperator();

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.delete("run-1"));
        verifyNoInteractions(runs, leases, purge, audit);
    }

    private static FeatureRun terminalRun() {
        FeatureRun run = new FeatureRun("org-1", "owner/repo", "issue-1", "title", "spec", 10, "GENERIC", 1);
        run.cancel();
        run.setArchived(true);
        return run;
    }
}
