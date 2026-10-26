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
    @Test
    void claimedIndependentTestRetryRetainsLatestCorrectionAfterReload() {
        FeatureRun run = new FeatureRun("org", "acme/repo", "issue-10", "Feature", "Must pass", 5,
                "GENERIC", "main", 1);
        DeliveryTask task = run.addPlannedTask("tests", "INDEPENDENT_TEST", "Tests", "provider", List.of("tests"), 2, 0);
        entityManager.persist(run);
        task.transition(TaskState.LEASED);
        task.transition(TaskState.REPAIR_QUEUED);
        entityManager.persist(new RepairPackage(task, "EXECUTION_FAILED", null));
        task.transition(TaskState.LEASED);
        task.transition(TaskState.REPAIR_QUEUED);
        entityManager.persist(new RepairPackage(task, "INVALID_PROVIDER_OUTPUT", null));
        task.transition(TaskState.LEASED);
        entityManager.flush();
        String taskId = task.getId();
        entityManager.clear();

        DeliveryTask stored = entityManager.find(DeliveryTask.class, taskId);
        assertEquals("INDEPENDENT_TEST", stored.getExecutionRole());
        org.junit.jupiter.api.Assertions.assertTrue(stored.getExecutionSpecification().contains("Failure category: INVALID_PROVIDER_OUTPUT"));
        org.junit.jupiter.api.Assertions.assertTrue(stored.getExecutionSpecification().contains("500 characters"));
    }

    @Test
    void repeatedProviderHoldsKeepUniqueContextWithoutSpendingRepairBudget() {
        FeatureRun run = new FeatureRun("org", "acme/repo", "issue-11", "Feature", "Must pass", 5,
                "GENERIC", "main", 1);
        DeliveryTask task = run.addPlannedTask("tests", "INDEPENDENT_TEST", "Tests", "provider", List.of("tests"), 2, 0);
        entityManager.persist(run);
        task.transition(TaskState.LEASED);
        task.hold();
        RepairPackage first = new RepairPackage(task, "OUTPUT_TOKEN_LIMIT", null);
        entityManager.persist(first);
        entityManager.flush();
        task.retryByOperator();
        task.transition(TaskState.LEASED);
        task.hold();
        RepairPackage second = new RepairPackage(task, "CONFIGURATION_ERROR", null);
        entityManager.persist(second);
        entityManager.flush();
        assertEquals(0, task.getAttempts());
        org.junit.jupiter.api.Assertions.assertTrue(second.getAttempt() > first.getAttempt());
    }

}
