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
        assertFalse(WorkerRolePolicy.ISSUE_SPECIFICATION.repositoryWrite());
        assertTrue(WorkerRolePolicy.ISSUE_SPECIFICATION.tools().contains("issue-proposal"));
        assertFalse(WorkerRolePolicy.AI_CHAT.repositoryWrite());
        assertTrue(WorkerRolePolicy.AI_CHAT.tools().isEmpty());
        assertTrue(WorkerRolePolicy.IMPLEMENTATION.tools().contains("gate-run"));
        assertTrue(WorkerRolePolicy.REPAIR.tools().contains("gate-run"));
    }

    @Test
    void unknownRolesFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> WorkerRolePolicy.require("UNREVIEWED_ROLE"));
    }
}
