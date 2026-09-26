package io.forgeloop.control.application;

import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.security.OperatorContext;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RunArchiveServiceTest {
    @Test void mutationRequiresRoleAndTenantLookupAndAuditsSuccess() {
        var runs=mock(FeatureRunService.class); var operators=mock(OperatorContext.class); var audit=mock(AuditLedgerService.class);
        var run=new FeatureRun("org","owner/repo","issue-1","title","spec",10,"GENERIC",1); run.cancel();
        when(runs.get("id")).thenReturn(run);
        new RunArchiveService(runs,operators,audit).archive("id",true);
        var ordered=inOrder(operators,runs,audit);
        ordered.verify(operators).requireOperator(); ordered.verify(runs).get("id");
        ordered.verify(audit).record("RUN_ARCHIVED","FEATURE_RUN","id","true");
        assertTrue(run.isArchived());
    }
    @Test void deniedTenantCannotMutateOrAudit() {
        var runs=mock(FeatureRunService.class); var operators=mock(OperatorContext.class); var audit=mock(AuditLedgerService.class);
        when(runs.get("other-org")).thenThrow(new IllegalStateException("Unavailable"));
        assertThrows(IllegalStateException.class,()->new RunArchiveService(runs,operators,audit).archive("other-org",true));
        verifyNoInteractions(audit);
    }
    @Test void viewerCannotReachRunLookup() {
        var runs=mock(FeatureRunService.class); var operators=mock(OperatorContext.class); var audit=mock(AuditLedgerService.class);
        doThrow(new org.springframework.security.access.AccessDeniedException("Denied")).when(operators).requireOperator();
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->new RunArchiveService(runs,operators,audit).archive("id",true));
        verifyNoInteractions(runs,audit);
    }
}
