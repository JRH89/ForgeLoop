package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import io.forgeloop.control.domain.*;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunnerEventServiceTest {
    TaskLeaseService leaseValidation=mock(TaskLeaseService.class); TaskLeaseRepository leases=mock(TaskLeaseRepository.class);
    DeliveryTaskRepository tasks=mock(DeliveryTaskRepository.class); RunnerEventRepository events=mock(RunnerEventRepository.class);
    RunnerEventService service=new RunnerEventService(leaseValidation,leases,tasks,events);

    @Test void appendsLeaseBoundEventIdempotently(){
        TaskLease lease=mock(TaskLease.class);DeliveryTask task=mock(DeliveryTask.class);FeatureRun run=mock(FeatureRun.class);
        when(leaseValidation.requireActiveTaskId("lease","runner","nonce")).thenReturn("task");when(leases.findById("lease")).thenReturn(Optional.of(lease));when(lease.getId()).thenReturn("lease");when(tasks.findById("task")).thenReturn(Optional.of(task));when(task.getRun()).thenReturn(run);when(run.getOrganizationId()).thenReturn("org");when(run.getId()).thenReturn("run");when(events.findByLeaseIdAndSequenceNumber("lease",1)).thenReturn(Optional.empty());when(events.save(any())).thenAnswer(call->call.getArgument(0));
        RunnerEvent recorded=service.append("lease","runner","nonce",1,"INFO","EXECUTION_STARTED","Bounded work started",Instant.now());
        assertEquals("EXECUTION_STARTED",recorded.getEventType());verify(events).save(any());
    }
    @Test void rejectsSecretsAndInvalidSequence(){
        assertThrows(IllegalArgumentException.class,()->service.append("l","r","n",0,"INFO","EXECUTION_STARTED","started",Instant.now()));
        when(leaseValidation.requireActiveTaskId("l","r","n")).thenReturn("task");
        assertThrows(IllegalArgumentException.class,()->service.append("l","r","n",1,"INFO","EXECUTION_STARTED","Authorization: Bearer secret-value",Instant.now()));
        verify(events,never()).save(any());
    }
    @Test void rejectsSequenceReplayWithDifferentContent(){
        RunnerEvent existing=new RunnerEvent("org","run","task","runner","lease",1,"INFO","EXECUTION_STARTED","first",Instant.EPOCH);when(leaseValidation.requireActiveTaskId("lease","runner","nonce")).thenReturn("task");when(events.findByLeaseIdAndSequenceNumber("lease",1)).thenReturn(Optional.of(existing));
        assertThrows(IllegalStateException.class,()->service.append("lease","runner","nonce",1,"INFO","EXECUTION_STARTED","different",Instant.EPOCH));
    }
}
