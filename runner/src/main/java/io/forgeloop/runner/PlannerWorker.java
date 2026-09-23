package io.forgeloop.runner;

/** Invokes the policy-selected provider and accepts only the exact planner JSON contract. */
public final class PlannerWorker {
    public PlannerResult execute(ProviderExecutionPolicy policy, ProviderClient provider, RunnerTask task, String repositoryContext, String correlationId) throws ProviderExecutionFailure {
        String instructions = "Return JSON only with exact fields: "
                + "{acceptanceCriteria:[string],tasks:[{key:string,role:string,title:string,requiredCapability:string,"
                + "dependencies:[string],ownedPaths:[string],attemptBudget:integer,budgetMicros:integer}]}. "
                + "Roles: IMPLEMENTATION, BACKEND, FRONTEND, INDEPENDENT_TEST, INTEGRATION. Required capability must be provider for writing roles and git for INTEGRATION. Every writing task must have an empty dependencies array and non-overlapping owned paths so isolated workers can run in parallel. Return exactly one pathless INTEGRATION task that depends on every writing task. Use only paths present in the repository manifest unless the specification explicitly requires a new file. The server adds independent review, repair routing, and verification tasks. "
                + "Use repository-relative owned path prefixes, an acyclic graph, 1-5 attempts per task, and stay within the stated budget.";
        String input = "Title: " + task.title() + "\nRun budget USD: " + task.budgetUsd() + "\nSpecification:\n" + task.specification()
                + "\n\nBounded repository context:\n" + repositoryContext;
        ProviderExecutionResult execution = new ProviderExecutionService().executeDetailed(provider,
                new ProviderRequest(policy.model(), instructions, input, 8192, StructuredOutputSchemas.plan()), policy.maxAttempts());
        ProviderUsageEvidence usage = ProviderUsageEvidence.from(policy, execution, ProviderCostEstimate.unknown(), correlationId);
        try {
            return new PlannerResult(PlannerPlan.parse(execution.result().output()).validate(task.budgetUsd()), usage);
        } catch (IllegalArgumentException invalidOutput) {
            throw new PlannerOutputFailure(usage, invalidOutput);
        }
    }
}
