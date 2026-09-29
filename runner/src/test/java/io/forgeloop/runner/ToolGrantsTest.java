package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ToolGrantsTest {
    @Test
    void codeRolesReceiveOnlyTheSixOrSevenToolsTheirGrantsMapTo() {
        for (String role : Set.of("IMPLEMENTATION", "BACKEND", "FRONTEND", "INDEPENDENT_TEST", "REPAIR")) {
            Set<String> withoutGate = ToolGrants.forRole(role, false);
            assertEquals(6, withoutGate.size());
            assertFalse(withoutGate.contains("run_gate"));
            Set<String> withGate = ToolGrants.forRole(role, true);
            assertEquals(7, withGate.size());
            assertTrue(withGate.contains("run_gate"));
        }
    }

    @Test
    void nonWritingRolesAndUnknownGrantStringsCannotAcquireTools() {
        for (String role : Set.of("PLANNER", "REVIEW", "INTEGRATION", "REPOSITORY_SCAN")) {
            Set<String> readable = ToolGrants.forRole(role, true);
            assertEquals(Set.of("list_files", "read_file", "search_files"), readable);
            assertFalse(readable.contains("finish"));
            assertFalse(readable.contains("write_file"));
            assertFalse(readable.contains("run_gate"));
        }
        assertTrue(ToolGrants.forRole("ISSUE_SPECIFICATION", true).isEmpty());
        assertTrue(ToolGrants.forRole("AI_CHAT", true).isEmpty());
    }
}
