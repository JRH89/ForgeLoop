package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PlannerPlanTest {
    @Test
    void parsesAndValidatesExactAcyclicPlan() {
        PlannerPlan plan = PlannerPlan.parse("""
                {"acceptanceCriteria":["works"],"tasks":[
                  {"key":"backend","role":"BACKEND","title":"Implement","requiredCapability":"provider","dependencies":[],"ownedPaths":["src"],"attemptBudget":2,"budgetMicros":500000},
                  {"key":"test","role":"INDEPENDENT_TEST","title":"Test","requiredCapability":"provider","dependencies":[],"ownedPaths":["tests"],"attemptBudget":2,"budgetMicros":500000},
                  {"key":"integration","role":"INTEGRATION","title":"Integrate","requiredCapability":"git","dependencies":["backend","test"],"ownedPaths":[],"attemptBudget":2,"budgetMicros":0}
                ]}
                """).validate(1);

        assertEquals(3, plan.tasks().size());
    }

    @Test
    void rejectsExtraFieldsCyclesAndUnsafePaths() {
        assertThrows(IllegalArgumentException.class, () -> PlannerPlan.parse("{\"acceptanceCriteria\":[\"x\"],\"tasks\":[],\"extra\":true}"));
        assertThrows(IllegalArgumentException.class, () -> PlannerPlan.parse("""
                {"acceptanceCriteria":["x"],"tasks":[
                  {"key":"a","role":"BACKEND","title":"A","requiredCapability":"provider","dependencies":["b"],"ownedPaths":["../src"],"attemptBudget":2,"budgetMicros":1},
                  {"key":"b","role":"FRONTEND","title":"B","requiredCapability":"provider","dependencies":["a"],"ownedPaths":["web"],"attemptBudget":2,"budgetMicros":1}
                ]}
                """).validate(1));
    }

    @Test
    void rejectsDependenciesBetweenIsolatedWritingTasks() {
        assertThrows(IllegalArgumentException.class, () -> PlannerPlan.parse("""
                {"acceptanceCriteria":["x"],"tasks":[
                  {"key":"implementation","role":"IMPLEMENTATION","title":"Implement","requiredCapability":"provider","dependencies":[],"ownedPaths":["src"],"attemptBudget":2,"budgetMicros":1},
                  {"key":"test","role":"INDEPENDENT_TEST","title":"Test","requiredCapability":"provider","dependencies":["implementation"],"ownedPaths":["tests"],"attemptBudget":2,"budgetMicros":1},
                  {"key":"integration","role":"INTEGRATION","title":"Integrate","requiredCapability":"git","dependencies":["implementation","test"],"ownedPaths":[],"attemptBudget":2,"budgetMicros":0}
                ]}
                """).validate(1));
    }
}
