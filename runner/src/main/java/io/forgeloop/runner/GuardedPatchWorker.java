package io.forgeloop.runner;

import java.nio.file.Path;
import java.util.List;

/** Executes one code-producing worker with no shell access and writes only schema-validated, prefix-scoped files. */
public final class GuardedPatchWorker {
    public static boolean supports(String role) {
        try { return WorkerRolePolicy.require(role).repositoryWrite(); }
        catch (IllegalArgumentException exception) { return false; }
    }
    public GuardedPatchResult execute(ProviderExecutionPolicy policy, ProviderClient provider, String role,
                                      String title, String specification, Path worktree,
                                      List<String> allowedPrefixes) throws Exception {
        return execute(policy, provider, role, title, specification, worktree, allowedPrefixes, java.util.UUID.randomUUID().toString());
    }
    public GuardedPatchResult execute(ProviderExecutionPolicy policy, ProviderClient provider, String role,
                                      String title, String specification, Path worktree,
                                      List<String> allowedPrefixes, String correlationId) throws Exception {
        if (!supports(role)) {
            throw new IllegalArgumentException("Task role is not permitted to modify repository files");
        }
        String instructions = "You are the " + role + " worker. Return JSON only: "
                + "{summary:string,changes:[{path:string,content:string,message:string}]}. "
                + "Propose complete file contents only. Do not use paths outside the allowed prefixes.";
        String input = "Task: " + title + "\nAllowed prefixes: " + String.join(",", allowedPrefixes)
                + "\nSpecification:\n" + specification + "\n\nBounded repository context:\n"
                + new RepositoryContextBuilder().build(worktree, allowedPrefixes);
        ProviderExecutionResult execution = new ProviderExecutionService().executeDetailed(provider,
                new ProviderRequest(policy.model(), instructions, input, 8192, StructuredOutputSchemas.patch()), policy.maxAttempts());
        ProviderUsageEvidence usage = ProviderUsageEvidence.from(policy, execution,
                new ProviderCostCalculator().fromEnvironment(policy, execution.result()), correlationId);
        PatchPlan plan;
        try {
            plan = PatchPlan.parse(execution.result().output());
            new PatchWriter().apply(worktree, plan, allowedPrefixes);
        } catch (Exception unsafeOutput) {
            throw new GuardedPatchFailure("INVALID_PROVIDER_OUTPUT", usage, unsafeOutput);
        }
        try {
            String sha = new GitWorktreeManager().commit(worktree, commitMessage(plan.summary()));
            return new GuardedPatchResult(sha, usage);
        } catch (Exception localFailure) {
            throw new GuardedPatchFailure("LOCAL_COMMIT_FAILURE", usage, localFailure);
        }
    }

    /** Normalizes untrusted prose into one bounded Git subject without discarding an otherwise safe patch. */
    static String commitMessage(String summary) {
        String normalized = summary.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").strip();
        String message = "forgeloop: " + normalized;
        return message.substring(0, Math.min(message.length(), 200));
    }
}
