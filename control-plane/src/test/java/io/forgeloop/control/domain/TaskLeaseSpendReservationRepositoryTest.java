package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import io.forgeloop.control.application.RunExitMeaning;
import io.forgeloop.control.application.RunExitMeaningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:lease-spend-reservations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class TaskLeaseSpendReservationRepositoryTest {
    @Autowired private EntityManager entityManager;
    @Autowired private TaskLeaseRepository leases;
    @Autowired private DeliveryTaskRepository tasks;
    @Autowired private HumanEscalationRepository escalations;

    @Test
    void sumsOnlyUnexpiredIncompleteSiblingReservations() throws Exception {
        FeatureRun run = new FeatureRun("org", "owner/repository", "issue-1", "Title", "Spec", 5.0,
                "GENERIC", "main", 1);
        DeliveryTask countedTask = run.addPlannedTask("counted", "IMPLEMENTATION", "Counted", "provider", List.of("src/a"), 1, 1_000);
        DeliveryTask expiredTask = run.addPlannedTask("expired", "IMPLEMENTATION", "Expired", "provider", List.of("src/b"), 1, 1_000);
        DeliveryTask completedTask = run.addPlannedTask("completed", "IMPLEMENTATION", "Completed", "provider", List.of("src/c"), 1, 1_000);
        DeliveryTask excludedTask = run.addPlannedTask("excluded", "IMPLEMENTATION", "Excluded", "provider", List.of("src/d"), 1, 1_000);
        Runner runner = new Runner("org", "runner", "1", List.of("provider"), "credential-hash");
        TaskLease counted = acknowledgedLease(countedTask, runner, "counted"); counted.reserve(100);
        TaskLease expired = acknowledgedLease(expiredTask, runner, "expired"); expired.reserve(200);
        TaskLease completed = acknowledgedLease(completedTask, runner, "completed"); completed.reserve(300); completed.closeForHold();
        TaskLease excluded = acknowledgedLease(excludedTask, runner, "excluded"); excluded.reserve(400);
        set(expired, "expiresAt", Instant.now().minusSeconds(1));

        entityManager.persist(run);
        entityManager.persist(runner);
        entityManager.persist(counted);
        entityManager.persist(expired);
        entityManager.persist(completed);
        entityManager.persist(excluded);
        entityManager.flush();
        entityManager.createNativeQuery("update task_lease set reserved_micros = 300 where id = :id")
                .setParameter("id", completed.getId()).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        long activeSiblings = leases.sumActiveReservationsByRunExcludingLease(run.getId(), excluded.getId(), Instant.now());

        assertEquals(100, activeSiblings);
    }

    @Test
    void derivesHeldRunMeaningFromItsLatestClosedLeaseAndOpenEscalation() {
        FeatureRun run = new FeatureRun("org", "owner/held", "issue-2", "Held run", "Spec", 5.0,
                "GENERIC", "main", 1);
        DeliveryTask task = run.addPlannedTask("held", "IMPLEMENTATION", "Hold for operator", "provider",
                List.of("src/held"), 1, 1_000);
        task.transition(TaskState.LEASED);
        task.hold();
        run.block();
        Runner runner = new Runner("org", "runner", "1", List.of("provider"), "credential-hash");
        TaskLease lease = acknowledgedLease(task, runner, "held");
        lease.closeForHold("LOOP_BUDGET_EXHAUSTED");

        entityManager.persist(run);
        entityManager.persist(runner);
        entityManager.persist(lease);
        HumanEscalation escalation = new HumanEscalation(run, task, "LOOP_BUDGET_EXHAUSTED", "HIGH",
                "The agent loop reached its configured bound.");
        entityManager.persist(escalation);
        entityManager.flush();
        entityManager.clear();

        RunExitMeaning meaning = new RunExitMeaningService(leases, escalations, tasks).derive(List.of(run)).get(run.getId());

        assertEquals(AttemptOutcome.STOPPED, meaning.outcome());
        assertEquals("LOOP_BUDGET_EXHAUSTED", meaning.reason());
    }

    private static TaskLease acknowledgedLease(DeliveryTask task, Runner runner, String nonce) {
        String hash;
        try {
            hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(nonce.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        TaskLease lease = new TaskLease(task, runner, hash, Instant.now().plusSeconds(600));
        lease.acknowledge();
        return lease;
    }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = TaskLease.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
