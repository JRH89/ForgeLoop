package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PlannerWorkerTest {
    @Test
    void plannerInstructionsDescribeSequencedWritingDependencies() throws Exception {
        AtomicReference<ProviderRequest> captured = new AtomicReference<>();
        ProviderClient provider = request -> {
            captured.set(request);
            return new ProviderResult("""
                    {"acceptanceCriteria":["works"],"tasks":[
                      {"key":"tests","role":"INDEPENDENT_TEST","title":"Tests","requiredCapability":"provider","dependencies":[],"ownedPaths":["tests"],"attemptBudget":1,"budgetMicros":0},
                      {"key":"implementation","role":"IMPLEMENTATION","title":"Implement","requiredCapability":"provider","dependencies":["tests"],"ownedPaths":["src"],"attemptBudget":1,"budgetMicros":0},
                      {"key":"integration","role":"INTEGRATION","title":"Integrate","requiredCapability":"git","dependencies":["implementation","tests"],"ownedPaths":[],"attemptBudget":1,"budgetMicros":0}
                    ]}
                    """, 1, 1, "planner-request");
        };
        RunnerTask task = new RunnerTask("task-1", "PLANNER", "Plan", "org/repository", "main", null, "spec", "provider");

        new PlannerWorker().execute(new ProviderExecutionPolicy("openai", "model", 1), provider, task, "manifest", "plan-1");

        assertTrue(captured.get().instructions().contains("at most one dependency on another writing task"));
        assertTrue(captured.get().instructions().contains("starts from that task's commit"));
    }

    @Test
    void testFirstInstructionsAreConditionalAndNameTheTestGlobs() {
        RunnerTask ordinary = new RunnerTask("task-1", "PLANNER", "Plan", "org/repository", "main", null, "spec", "provider");
        RunnerTask testFirst = new RunnerTask("task-2", "PLANNER", "Plan", "org/repository", "main", null, "spec", "provider",
                1, java.util.List.of(), java.util.List.of(), null, null, null, java.util.List.of(), null, null,
                "main", "main", java.util.List.of(), java.util.List.of(), "ANY", java.util.List.of("**/*.test.ts"), null);

        assertFalse(PlannerWorker.instructionsFor(ordinary).contains("Test-first delivery is required"));
        assertTrue(PlannerWorker.instructionsFor(testFirst).contains("Test-first delivery is required"));
        assertTrue(PlannerWorker.instructionsFor(testFirst).contains("**/*.test.ts"));
    }
}
