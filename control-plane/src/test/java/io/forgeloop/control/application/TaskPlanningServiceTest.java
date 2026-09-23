package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.DeliveryTaskRepository;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.FeatureRunRepository;
import io.forgeloop.control.domain.RunState;
import io.forgeloop.control.domain.TaskLease;
import io.forgeloop.control.domain.TaskLeaseRepository;
import io.forgeloop.control.domain.TaskState;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TaskPlanningServiceTest {
    private final DeliveryTaskRepository tasks = mock(DeliveryTaskRepository.class);
    private final FeatureRunRepository runs = mock(FeatureRunRepository.class);
    private final AuditLedgerService audit = mock(AuditLedgerService.class);
    private final TaskLeaseRepository leases = mock(TaskLeaseRepository.class);
    private final TaskPlanningService planning = new TaskPlanningService(tasks, runs, new TaskGraphValidator(), audit, leases);

    @Test
    void materializesDependenciesAndClosesPlannerExactlyOnce() {
        FeatureRun run = planningRun();
        DeliveryTask planner = run.getTasks().getFirst();
        TaskLease lease = mock(TaskLease.class);
        when(tasks.findById("planner")).thenReturn(Optional.of(planner));
        when(leases.findFirstByTask_IdOrderByExpiresAtDesc("planner")).thenReturn(Optional.of(lease));
        when(runs.save(any())).thenAnswer(call -> call.getArgument(0));
        TaskPlanSubmission plan = new TaskPlanSubmission(List.of("behavior is verified"), List.of(
                new PlannedTaskSubmission("backend", "BACKEND", "Backend", "provider", List.of(), List.of("src"), 2, 500_000),
                new PlannedTaskSubmission("test", "INDEPENDENT_TEST", "Test", "provider", List.of(), List.of("tests"), 2, 500_000),
                new PlannedTaskSubmission("integration", "INTEGRATION", "Integrate", "git", List.of("backend","test"), List.of(), 2, 0)));

        FeatureRun saved = planning.submit("planner", plan);

        assertEquals(RunState.QUEUED, saved.getState());
        assertEquals(TaskState.VERIFIED, planner.getState());
        assertEquals(List.of("backend", "test"), saved.getTasks().get(3).getDependencyKeys());
        verify(lease).completePlanning();
    }

    @Test
    void rejectsSecondMaterialization() {
        FeatureRun run = planningRun();
        DeliveryTask planner = run.getTasks().getFirst();
        run.addPlannedTask("existing", "BACKEND", "Existing", "provider", List.of("src"), 2, 1);
        when(tasks.findById("planner")).thenReturn(Optional.of(planner));

        assertThrows(IllegalStateException.class, () -> planning.submit("planner",
                new TaskPlanSubmission(List.of("criterion"), List.of(
                        new PlannedTaskSubmission("new", "BACKEND", "New", "provider", List.of(), List.of("src"), 2, 1),
                        new PlannedTaskSubmission("integration", "INTEGRATION", "Integrate", "git", List.of("new"), List.of(), 2, 0)))));
    }

    private static FeatureRun planningRun() {
        FeatureRun run = new FeatureRun("org", "owner/repo", "issue-1", "Feature", "spec", 1, "GENERIC", "main", 1);
        run.addTask("PLANNER", "Plan", "provider");
        run.beginPlanning();
        run.getTasks().getFirst().transition(TaskState.LEASED);
        return run;
    }
}
