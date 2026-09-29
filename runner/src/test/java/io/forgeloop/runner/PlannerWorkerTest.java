package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
