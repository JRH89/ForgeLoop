package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorkerRolePolicyTest {
    @Test
    void onlyCodeProducingRolesReceiveRepositoryWriteCapability() {
        assertTrue(WorkerRolePolicy.IMPLEMENTATION.repositoryWrite());
        assertTrue(WorkerRolePolicy.BACKEND.repositoryWrite());
        assertTrue(WorkerRolePolicy.FRONTEND.repositoryWrite());
        assertTrue(WorkerRolePolicy.INDEPENDENT_TEST.repositoryWrite());
        assertTrue(WorkerRolePolicy.REPAIR.repositoryWrite());
        assertFalse(WorkerRolePolicy.PLANNER.repositoryWrite());
        assertFalse(WorkerRolePolicy.INTEGRATION.repositoryWrite());
        assertFalse(WorkerRolePolicy.REVIEW.repositoryWrite());
        assertFalse(WorkerRolePolicy.REPOSITORY_SCAN.repositoryWrite());
        assertTrue(WorkerRolePolicy.REPOSITORY_SCAN.tools().contains("repository-read"));
        assertFalse(WorkerRolePolicy.REPOSITORY_SCAN.tools().contains("scoped-file-write"));
    }

    @Test
    void unknownRolesFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> WorkerRolePolicy.require("UNREVIEWED_ROLE"));
    }
}
