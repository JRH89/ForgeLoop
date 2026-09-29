package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class DeliveryTaskWriteBoundaryTest {
    @Test
    void derivesBoundariesFromStoredRolesAndKeepsTestWriterBoundaryOnRetry() {
        FeatureRun ordinary = new FeatureRun("acme/repo", "issue-1", "title", "spec", 1, "GENERIC", 1);
        DeliveryTask ordinaryImplementation = ordinary.addPlannedTask("ordinary", "IMPLEMENTATION", "Implement", "provider", List.of("src"), 2, 10);
        assertEquals("ANY", ordinaryImplementation.getWriteBoundary());

        FeatureRun testFirst = new FeatureRun("org", "acme/repo", "issue-2", "title", "spec", 1, "GENERIC", "main", 1);
        testFirst.snapshotTestFirst("unit", List.of("**/*.test.ts"));
        DeliveryTask testWriter = testFirst.addPlannedTask("tests", "INDEPENDENT_TEST", "Tests", "provider", List.of("src"), 2, 10);
        DeliveryTask implementation = testFirst.addPlannedTask("implementation", "IMPLEMENTATION", "Implement", "provider", List.of("src"), 2, 10);
        DeliveryTask qualityRepair = testFirst.addPlannedTask("repair", "REPAIR", "Repair", "provider", List.of("src"), 2, 10);

        testWriter.transition(TaskState.LEASED);
        testWriter.transition(TaskState.REPAIR_QUEUED);

        assertEquals("REPAIR", testWriter.getExecutionRole());
        assertEquals("TESTS_ONLY", testWriter.getWriteBoundary());
        assertEquals("NO_TESTS", implementation.getWriteBoundary());
        assertEquals("NO_TESTS", qualityRepair.getWriteBoundary());
        assertEquals(List.of("**/*.test.ts"), testWriter.getTestPathGlobs());
    }
}
