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
        return execute(policy, provider, role, title, specification, worktree, allowedPrefixes, allowedPrefixes,
                java.util.UUID.randomUUID().toString());
    }
    public GuardedPatchResult execute(ProviderExecutionPolicy policy, ProviderClient provider, String role,
                                      String title, String specification, Path worktree,
                                      List<String> allowedPrefixes, String correlationId) throws Exception {
        return execute(policy, provider, role, title, specification, worktree, allowedPrefixes, allowedPrefixes, correlationId);
    }
    /** Prioritizes useful repository context independently from the immutable write boundary. */
    public GuardedPatchResult execute(ProviderExecutionPolicy policy, ProviderClient provider, String role,
                                      String title, String specification, Path worktree,
                                      List<String> allowedPrefixes, List<String> preferredContextPaths,
                                      String correlationId) throws Exception {
        return execute(policy, provider, role, title, specification, worktree, allowedPrefixes,
                preferredContextPaths, correlationId, WriteBoundary.any());
    }

    /** Applies server-derived test-file restrictions in addition to repository-owned path prefixes. */
    public GuardedPatchResult execute(ProviderExecutionPolicy policy, ProviderClient provider, String role,
                                      String title, String specification, Path worktree,
                                      List<String> allowedPrefixes, List<String> preferredContextPaths,
                                      String correlationId, WriteBoundary boundary) throws Exception {
        return execute(policy, provider, role, title, specification, worktree, allowedPrefixes,
                preferredContextPaths, correlationId, boundary, null);
    }

    /** Optional journal records the exact context identity before an opted-in provider call. */
    public GuardedPatchResult execute(ProviderExecutionPolicy policy, ProviderClient provider, String role,
                                      String title, String specification, Path worktree,
                                      List<String> allowedPrefixes, List<String> preferredContextPaths,
                                      String correlationId, WriteBoundary boundary, StepJournal journal) throws Exception {
        if (!supports(role)) {
            throw new IllegalArgumentException("Task role is not permitted to modify repository files");
        }
        String instructions = "You are the " + role + " worker. Return JSON only: "
                + "{summary:string,changes:[{path:string,content:string,message:string}]}. "
                + "Propose complete file contents only. Do not use paths outside the allowed prefixes."
                + boundaryInstructions(boundary);
        String repositoryContext = new RepositoryContextBuilder().build(worktree, preferredContextPaths);
        if (journal != null) journal.append("CONTEXT_BUILT", java.util.Map.of("kind", "REPOSITORY",
                "sha256", EvidenceDigests.sha256(repositoryContext.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        String input = "Task: " + title + "\nAllowed prefixes: " + String.join(",", allowedPrefixes)
                + "\nSpecification:\n" + specification + "\n\nBounded repository context:\n" + repositoryContext;
        ProviderExecutionResult execution = new ProviderExecutionService().executeDetailed(provider,
                new ProviderRequest(policy.model(), instructions, input, 8192, StructuredOutputSchemas.patch()), policy.maxAttempts());
        ProviderUsageEvidence usage = ProviderUsageEvidence.from(policy, execution,
                new ProviderCostCalculator().fromEnvironment(policy, execution.result()), correlationId);
        PatchPlan plan;
        try {
            plan = PatchPlan.parse(execution.result().output());
            new PatchWriter().apply(worktree, plan, allowedPrefixes, boundary);
        } catch (WriteBoundaryViolation boundaryViolation) {
            throw new GuardedPatchFailure("TEST_BOUNDARY_VIOLATION", usage, boundaryViolation);
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

    static String boundaryInstructions(WriteBoundary boundary) {
        if (boundary == null || boundary.isAny()) return "";
        if (boundary.requiresTestPaths()) return " Test files are paths matching: "
                + String.join(",", boundary.testPathGlobs()) + ". You may write only test files.";
        return " You may not write test files.";
    }

    /** Normalizes untrusted prose into one bounded Git subject without discarding an otherwise safe patch. */
    static String commitMessage(String summary) {
        String normalized = summary.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").strip();
        String message = "forgeloop: " + normalized;
        return message.substring(0, Math.min(message.length(), 200));
    }
}
