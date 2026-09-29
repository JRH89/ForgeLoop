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
    void acceptsAChainOfWritingTasks() {
        PlannerPlan plan = PlannerPlan.parse("""
                {"acceptanceCriteria":["x"],"tasks":[
                  {"key":"tests","role":"INDEPENDENT_TEST","title":"Tests","requiredCapability":"provider","dependencies":[],"ownedPaths":["tests"],"attemptBudget":2,"budgetMicros":1},
                  {"key":"backend","role":"BACKEND","title":"Backend","requiredCapability":"provider","dependencies":["tests"],"ownedPaths":["backend"],"attemptBudget":2,"budgetMicros":1},
                  {"key":"implementation","role":"IMPLEMENTATION","title":"Implement","requiredCapability":"provider","dependencies":["backend"],"ownedPaths":["src"],"attemptBudget":2,"budgetMicros":1},
                  {"key":"integration","role":"INTEGRATION","title":"Integrate","requiredCapability":"git","dependencies":["implementation","backend","tests"],"ownedPaths":[],"attemptBudget":2,"budgetMicros":0}
                ]}
                """).validate(1);

        assertEquals(4, plan.tasks().size());
    }

    @Test
    void rejectsMultipleWritingDependenciesAndNonWritingDependencies() {
        String multipleWritingDependencies = """
                {"acceptanceCriteria":["x"],"tasks":[
                  {"key":"tests","role":"INDEPENDENT_TEST","title":"Tests","requiredCapability":"provider","dependencies":[],"ownedPaths":["tests"],"attemptBudget":2,"budgetMicros":1},
                  {"key":"backend","role":"BACKEND","title":"Backend","requiredCapability":"provider","dependencies":[],"ownedPaths":["backend"],"attemptBudget":2,"budgetMicros":1},
                  {"key":"implementation","role":"IMPLEMENTATION","title":"Implement","requiredCapability":"provider","dependencies":["tests","backend"],"ownedPaths":["src"],"attemptBudget":2,"budgetMicros":1},
                  {"key":"integration","role":"INTEGRATION","title":"Integrate","requiredCapability":"git","dependencies":["tests","backend","implementation"],"ownedPaths":[],"attemptBudget":2,"budgetMicros":0}
                ]}
                """;
        String nonWritingDependency = """
                {"acceptanceCriteria":["x"],"tasks":[
                  {"key":"implementation","role":"IMPLEMENTATION","title":"Implement","requiredCapability":"provider","dependencies":[],"ownedPaths":["src"],"attemptBudget":2,"budgetMicros":1},
                  {"key":"integration","role":"INTEGRATION","title":"Integrate","requiredCapability":"git","dependencies":["implementation"],"ownedPaths":[],"attemptBudget":2,"budgetMicros":0},
                  {"key":"dependent","role":"BACKEND","title":"Dependent","requiredCapability":"provider","dependencies":["integration"],"ownedPaths":["backend"],"attemptBudget":2,"budgetMicros":1}
                ]}
                """;

        assertThrows(IllegalArgumentException.class, () -> PlannerPlan.parse(multipleWritingDependencies).validate(1));
        assertThrows(IllegalArgumentException.class, () -> PlannerPlan.parse(nonWritingDependency).validate(1));
    }

    @Test
    void rejectsCyclesBetweenWritingTasks() {
        String cycle = """
                {"acceptanceCriteria":["x"],"tasks":[
                  {"key":"backend","role":"BACKEND","title":"Backend","requiredCapability":"provider","dependencies":["frontend"],"ownedPaths":["backend"],"attemptBudget":2,"budgetMicros":1},
                  {"key":"frontend","role":"FRONTEND","title":"Frontend","requiredCapability":"provider","dependencies":["backend"],"ownedPaths":["frontend"],"attemptBudget":2,"budgetMicros":1},
                  {"key":"integration","role":"INTEGRATION","title":"Integrate","requiredCapability":"git","dependencies":["backend","frontend"],"ownedPaths":[],"attemptBudget":2,"budgetMicros":0}
                ]}
                """;

        assertThrows(IllegalArgumentException.class, () -> PlannerPlan.parse(cycle).validate(1));
    }

    @Test
    void validatesTestFirstScaffoldTestImplementationShapeOnlyWhenEnabled() {
        String valid = """
                {"acceptanceCriteria":["x"],"tasks":[
                  {"key":"scaffold","role":"IMPLEMENTATION","title":"Scaffold","requiredCapability":"provider","dependencies":[],"ownedPaths":["src/model"],"attemptBudget":2,"budgetMicros":1},
                  {"key":"tests","role":"INDEPENDENT_TEST","title":"Tests","requiredCapability":"provider","dependencies":["scaffold"],"ownedPaths":["src/test"],"attemptBudget":2,"budgetMicros":1},
                  {"key":"implementation","role":"BACKEND","title":"Implement","requiredCapability":"provider","dependencies":["tests"],"ownedPaths":["src/service"],"attemptBudget":2,"budgetMicros":1},
                  {"key":"integration","role":"INTEGRATION","title":"Integrate","requiredCapability":"git","dependencies":["scaffold","tests","implementation"],"ownedPaths":[],"attemptBudget":2,"budgetMicros":0}
                ]}
                """;
        String invalid = """
                {"acceptanceCriteria":["x"],"tasks":[
                  {"key":"implementation","role":"BACKEND","title":"Implement","requiredCapability":"provider","dependencies":[],"ownedPaths":["src"],"attemptBudget":2,"budgetMicros":1},
                  {"key":"tests","role":"INDEPENDENT_TEST","title":"Tests","requiredCapability":"provider","dependencies":[],"ownedPaths":["tests"],"attemptBudget":2,"budgetMicros":1},
                  {"key":"integration","role":"INTEGRATION","title":"Integrate","requiredCapability":"git","dependencies":["implementation","tests"],"ownedPaths":[],"attemptBudget":2,"budgetMicros":0}
                ]}
                """;

        assertEquals(4, PlannerPlan.parse(valid).validate(1, true).tasks().size());
        assertThrows(IllegalArgumentException.class, () -> PlannerPlan.parse(invalid).validate(1, true));
        assertEquals(3, PlannerPlan.parse(invalid).validate(1, false).tasks().size());
    }
}
