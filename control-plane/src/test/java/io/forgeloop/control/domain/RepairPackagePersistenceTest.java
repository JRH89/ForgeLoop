package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:repair-package;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class RepairPackagePersistenceTest {
    @Autowired private EntityManager entityManager;

    @Test
    void qualityRepairPackagePersistsItsNewTaskBeforeTheRunCollectionFlushes() {
        FeatureRun run = new FeatureRun("org", "acme/ticketly", "issue-9", "Feature", "Must pass", 5,
                "GENERIC", "main", 1);
        run.addCriterion("The feature works");
        run.addGate(new VerificationPolicySpec("verify", "CONTAINER", "image@sha256:" + "a".repeat(64),
                List.of("npm", "test"), "NONE", 300, true, "ALL"));
        run.addPlannedTask("implementation", "IMPLEMENTATION", "Implement", "provider", List.of("src"), 2, 0);
        run.addPlannedTask("integration", "INTEGRATION", "Integrate", "git", List.of(), 2, 0);
        run.addIndependentReviewTask();
        run.addPolicyVerificationTasks();
        entityManager.persist(run);
        entityManager.flush();

        DeliveryTask verification = run.getTasks().stream()
                .filter(task -> "VERIFICATION".equals(task.getRole())).findFirst().orElseThrow();
        verification.transition(TaskState.LEASED);
        RepairPackage repair = run.scheduleQualityRepair(verification, "VERIFICATION_FAILED", "d".repeat(64));

        entityManager.persist(repair);
        entityManager.flush();
        String repairTaskId = repair.getTaskId();
        assertNotNull(repairTaskId);

        entityManager.clear();
        RepairPackage stored = entityManager.find(RepairPackage.class, repair.getId());
        assertEquals(repairTaskId, stored.getTaskId());
        assertEquals("REPAIR", entityManager.find(DeliveryTask.class, repairTaskId).getRole());
    }
}
