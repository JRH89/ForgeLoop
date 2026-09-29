package io.forgeloop.runner;

/** Invokes the policy-selected provider and accepts only the exact planner JSON contract. */
public final class PlannerWorker {
    public PlannerResult execute(ProviderExecutionPolicy policy, ProviderClient provider, RunnerTask task, String repositoryContext, String correlationId) throws ProviderExecutionFailure {
        String instructions = instructionsFor(task);
        String input = "Title: " + task.title() + "\nRun budget USD: " + task.budgetUsd() + "\nSpecification:\n" + task.specification()
                + "\n\nBounded repository context:\n" + repositoryContext;
        ProviderExecutionResult execution = new ProviderExecutionService().executeDetailed(provider,
                new ProviderRequest(policy.model(), instructions, input, 8192, StructuredOutputSchemas.plan()), policy.maxAttempts());
        ProviderUsageEvidence usage = ProviderUsageEvidence.from(policy, execution, ProviderCostEstimate.unknown(), correlationId);
        try {
            boolean testFirst = !task.testPathGlobs().isEmpty();
            return new PlannerResult(PlannerPlan.parse(execution.result().output()).validate(task.budgetUsd(), testFirst), usage);
        } catch (IllegalArgumentException invalidOutput) {
            throw new PlannerOutputFailure(usage, invalidOutput);
        }
    }

    static String instructionsFor(RunnerTask task) {
        String instructions = "Return JSON only with exact fields: "
                + "{acceptanceCriteria:[string],tasks:[{key:string,role:string,title:string,requiredCapability:string,"
                + "dependencies:[string],ownedPaths:[string],attemptBudget:integer,budgetMicros:integer}]}. "
                + "Roles: IMPLEMENTATION, BACKEND, FRONTEND, INDEPENDENT_TEST, INTEGRATION. Required capability must be provider for writing roles and git for INTEGRATION. A writing task may have no dependency, or at most one dependency on another writing task when it must build on that task's files, in which case it starts from that task's commit. Keep owned paths non-overlapping for writing tasks that can run concurrently. Return exactly one pathless INTEGRATION task that depends on every writing task. Use only paths present in the repository manifest unless the specification explicitly requires a new file. The server adds independent review, repair routing, and verification tasks. "
                + "Use repository-relative owned path prefixes, an acyclic graph, 1-5 attempts per task, and stay within the stated budget.";
        if (!task.testPathGlobs().isEmpty()) instructions += " Test-first delivery is required. Test files are paths matching: "
                + String.join(",", task.testPathGlobs()) + ". For each behavior change, return an INDEPENDENT_TEST task that writes only test files, and an implementation task (IMPLEMENTATION, BACKEND or FRONTEND) that depends on exactly that test task and writes no test files. The new tests must compile and run against the code as it is before the implementation, and fail on assertions. If they need new types or signatures to compile, add a scaffold task (IMPLEMENTATION, BACKEND or FRONTEND, no dependencies, no test files) that adds only those, with bodies that do not implement the behavior, and make the test task depend on it. ForgeLoop runs the tests before and after the implementation, and rejects tests that pass, error or are skipped before it. Do not rename or delete existing tests.";
        return instructions;
    }
}
