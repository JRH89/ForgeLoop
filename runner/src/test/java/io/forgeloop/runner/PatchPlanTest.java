package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class PatchPlanTest {
    @Test void acceptsBoundedStructuredChanges() throws Exception {
        PatchPlan plan = PatchPlan.parse("{\"summary\":\"Add test\",\"changes\":[{\"path\":\"src/a.txt\",\"content\":\"ok\",\"message\":\"test: add a\"}]}");
        assertEquals("src/a.txt", plan.changes().getFirst().path());
    }
    @Test void acceptsSafeDotPrefixedRepositoryPaths() throws Exception {
        PatchPlan plan = PatchPlan.parse("{\"summary\":\"Update workflow\",\"changes\":[{\"path\":\".github/workflows/ci.yml\",\"content\":\"name: CI\",\"message\":\"ci: update workflow\"}]}");
        assertEquals(".github/workflows/ci.yml", plan.changes().getFirst().path());
    }
    @Test void rejectsTraversalAndProse() {
        assertThrows(IllegalArgumentException.class, () -> PatchPlan.parse("{\"summary\":\"x\",\"changes\":[{\"path\":\"../secret\",\"content\":\"x\",\"message\":\"x\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> PatchPlan.parse("{\"summary\":\"x\",\"changes\":[{\"path\":\"src//secret\",\"content\":\"x\",\"message\":\"x\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> PatchPlan.parse("{\"summary\":\"x\",\"changes\":[{\"path\":\"src/./secret\",\"content\":\"x\",\"message\":\"x\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> PatchPlan.parse("not json"));
    }
    @Test void rejectsUnexpectedFieldsAndDuplicatePaths() {
        assertThrows(IllegalArgumentException.class, () -> PatchPlan.parse("{\"summary\":\"x\",\"changes\":[{\"path\":\"src/a\",\"content\":\"x\",\"message\":\"x\",\"shell\":\"rm\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> PatchPlan.parse("{\"summary\":\"x\",\"changes\":[{\"path\":\"src/a\",\"content\":\"x\",\"message\":\"x\"},{\"path\":\"src/a\",\"content\":\"y\",\"message\":\"y\"}]}"));
    }
}
