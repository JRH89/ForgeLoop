package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class PatchPlanTest {
    @Test void acceptsBoundedStructuredChanges() throws Exception {
        PatchPlan plan = PatchPlan.parse("{\"summary\":\"Add test\",\"changes\":[{\"path\":\"src/a.txt\",\"content\":\"ok\",\"message\":\"test: add a\"}]}");
        assertEquals("src/a.txt", plan.changes().getFirst().path());
    }
    @Test void rejectsTraversalAndProse() {
        assertThrows(IllegalArgumentException.class, () -> PatchPlan.parse("{\"summary\":\"x\",\"changes\":[{\"path\":\"../secret\",\"content\":\"x\",\"message\":\"x\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> PatchPlan.parse("not json"));
    }
}
