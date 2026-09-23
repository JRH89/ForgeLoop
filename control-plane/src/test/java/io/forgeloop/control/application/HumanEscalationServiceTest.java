package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import io.forgeloop.control.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class HumanEscalationServiceTest {
    HumanEscalationRepository repository=mock(HumanEscalationRepository.class);HumanEscalationService service=new HumanEscalationService(repository);
    @Test void createsOneHighSeverityBudgetEscalation(){
        FeatureRun run=mock(FeatureRun.class);DeliveryTask task=mock(DeliveryTask.class);when(task.getRun()).thenReturn(run);when(run.getOrganizationId()).thenReturn("org");when(run.getId()).thenReturn("run");when(task.getId()).thenReturn("task");when(repository.findByRunIdAndTaskIdAndReason("run","task","BUDGET_EXHAUSTED")).thenReturn(Optional.empty());when(repository.save(any())).thenAnswer(call->call.getArgument(0));
        HumanEscalation item=service.escalate(task,"BUDGET_EXHAUSTED","Budget reached");
        assertEquals("HIGH",item.getSeverity());assertEquals("OPEN",item.getStatus());
    }
    @Test void enforcesTenantBoundaryAndLifecycle(){
        FeatureRun run=mock(FeatureRun.class);when(run.getOrganizationId()).thenReturn("org");HumanEscalation item=new HumanEscalation(run,null,"LEASE_EXPIRED","MEDIUM","Runner stopped");when(repository.findById("id")).thenReturn(Optional.of(item));
        assertThrows(IllegalArgumentException.class,()->service.acknowledge("id","other","operator"));
        assertEquals("ACKNOWLEDGED",service.acknowledge("id","org","operator").getStatus());assertEquals("RESOLVED",service.resolve("id","org","operator").getStatus());
    }
}
