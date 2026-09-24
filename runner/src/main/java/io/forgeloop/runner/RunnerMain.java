package io.forgeloop.runner;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;

/**
 * CLI entry point for explicit, operator-initiated runner registration.
 * Registration tokens are accepted only as arguments and are never written to stdout.
 */
public final class RunnerMain {
    private RunnerMain() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length > 0 && ("serve".equals(arguments[0]) || "work-until-idle".equals(arguments[0]))) {
            workLoop(arguments, "serve".equals(arguments[0]));
            return;
        }
        if (arguments.length > 0 && "heartbeat".equals(arguments[0])) {
            heartbeat(arguments);
            return;
        }
        if (arguments.length > 0 && "available-tasks".equals(arguments[0])) {
            availableTasks(arguments);
            return;
        }
        if (arguments.length > 0 && "provider-health".equals(arguments[0])) {
            providerHealth(arguments);
            return;
        }
        if (arguments.length > 0 && "generate-patch".equals(arguments[0])) {
            generatePatch(arguments);
            return;
        }
        if (arguments.length > 0 && "execute-provider-task".equals(arguments[0])) {
            executeProviderTask(arguments);
            return;
        }
        if (arguments.length > 0 && "execute-policy-task".equals(arguments[0])) {
            executePolicyTask(arguments);
            return;
        }
        if (arguments.length > 0 && "claim-task".equals(arguments[0])) {
            claimTask(arguments);
            return;
        }
        if (arguments.length > 0 && "acknowledge-lease".equals(arguments[0])) {
            acknowledgeLease(arguments);
            return;
        }
        if (arguments.length > 0 && "complete-lease".equals(arguments[0])) {
            completeLease(arguments);
            return;
        }
        if (arguments.length > 0 && "acknowledge-claimed-lease".equals(arguments[0])) {
            acknowledgeClaimedLease(arguments);
            return;
        }
        if (arguments.length > 0 && "prepare-worktree".equals(arguments[0])) {
            prepareWorktree(arguments);
            return;
        }
        if (arguments.length > 0 && "claim-and-prepare-task".equals(arguments[0])) {
            claimAndPrepareTask(arguments);
            return;
        }
        if (arguments.length > 0 && "remove-worktree".equals(arguments[0])) {
            removeWorktree(arguments);
            return;
        }
        if (arguments.length > 0 && "verify".equals(arguments[0])) {
            verify(arguments);
            return;
        }
        if (arguments.length > 0 && "verify-container".equals(arguments[0])) {
            verifyContainer(arguments);
            return;
        }
        if (arguments.length > 0 && "verify-container-and-record".equals(arguments[0])) {
            verifyContainerAndRecord(arguments);
            return;
        }
        if (arguments.length > 0 && "verify-container-gate-and-record".equals(arguments[0])) {
            verifyContainerGateAndRecord(arguments);
            return;
        }
        if (arguments.length > 0 && "verify-container-and-bundle".equals(arguments[0])) {
            verifyContainerAndBundle(arguments);
            return;
        }
        RunnerConfig config = configFrom(arguments);
        RunnerIdentity identity = new RunnerClient(HttpClient.newHttpClient(), config.controlPlane()).register(config);
        Path statePath = statePath();
        new RunnerIdentityStore().save(statePath, identity);
        System.out.println("Runner enrolled. Local state saved to " + statePath + ".");
    }

    static RunnerConfig configFrom(String[] arguments) {
        if (arguments.length != 5 || !"register".equals(arguments[0])) {
            throw new IllegalArgumentException(
                    "Usage: register <control-plane-url> <token> <name> <capabilities-comma-separated>");
        }
        return new RunnerConfig(
                URI.create(arguments[1]),
                arguments[2],
                arguments[3],
                "0.1.0",
                List.of(arguments[4].split(",")));
    }

    private static void heartbeat(String[] arguments) throws Exception {
        if (arguments.length != 3) throw new IllegalArgumentException("Usage: heartbeat <control-plane-url> <state-file>");
        RunnerIdentity identity = new RunnerIdentityStore().load(Path.of(arguments[2]));
        new RunnerClient(HttpClient.newHttpClient(), URI.create(arguments[1])).heartbeat(identity);
        System.out.println("Runner heartbeat accepted.");
    }

    private static void availableTasks(String[] arguments) throws Exception {
        if (arguments.length != 3) throw new IllegalArgumentException("Usage: available-tasks <control-plane-url> <state-file>");
        RunnerIdentity identity = new RunnerIdentityStore().load(Path.of(arguments[2]));
        for (RunnerTask task : new RunnerClient(HttpClient.newHttpClient(), URI.create(arguments[1])).availableTasks(identity)) {
            System.out.println(task.id() + " " + task.role() + " " + task.repository() + "@" + task.baseBranch());
        }
    }

    private static void claimTask(String[] arguments) throws Exception {
        if (arguments.length != 5) throw new IllegalArgumentException("Usage: claim-task <control-plane-url> <identity-file> <task-id> <lease-file>");
        RunnerIdentity identity = new RunnerIdentityStore().load(Path.of(arguments[2]));
        RunnerLease lease = new RunnerClient(HttpClient.newHttpClient(), URI.create(arguments[1])).claimTask(identity, arguments[3]);
        new RunnerLeaseStore().save(Path.of(arguments[4]), lease);
        System.out.println("Task lease claimed: " + lease.leaseId());
    }

    private static void acknowledgeLease(String[] arguments) throws Exception {
        if (arguments.length != 5) throw new IllegalArgumentException("Usage: acknowledge-lease <control-plane-url> <state-file> <lease-id> <nonce>");
        RunnerIdentity identity = new RunnerIdentityStore().load(Path.of(arguments[2]));
        new RunnerClient(HttpClient.newHttpClient(), URI.create(arguments[1])).acknowledgeLease(identity, arguments[3], arguments[4]);
        System.out.println("Task lease acknowledged.");
    }

    private static void completeLease(String[] arguments) throws Exception {
        if (arguments.length != 6) throw new IllegalArgumentException("Usage: complete-lease <control-plane-url> <state-file> <lease-id> <nonce> <passed>");
        if (!"true".equals(arguments[5]) && !"false".equals(arguments[5])) {
            throw new IllegalArgumentException("Lease completion result must be true or false");
        }
        boolean passed = Boolean.parseBoolean(arguments[5]);
        RunnerIdentity identity = new RunnerIdentityStore().load(Path.of(arguments[2]));
        new RunnerClient(HttpClient.newHttpClient(), URI.create(arguments[1])).completeLease(identity, arguments[3], arguments[4], passed);
        System.out.println("Task lease completion accepted.");
    }

    private static void acknowledgeClaimedLease(String[] arguments) throws Exception {
        if (arguments.length != 4) throw new IllegalArgumentException("Usage: acknowledge-claimed-lease <control-plane-url> <identity-file> <lease-file>");
        RunnerIdentity identity = new RunnerIdentityStore().load(Path.of(arguments[2]));
        RunnerLease lease = new RunnerLeaseStore().load(Path.of(arguments[3]));
        new RunnerClient(HttpClient.newHttpClient(), URI.create(arguments[1])).acknowledgeLease(identity, lease.leaseId(), lease.nonce());
        System.out.println("Task lease acknowledged.");
    }

    private static void prepareWorktree(String[] arguments) throws Exception {
        if (arguments.length != 5) throw new IllegalArgumentException("Usage: prepare-worktree <repository-path> <base-ref> <task-id> <workspace-root>");
        Path worktree = new GitWorktreeManager().create(Path.of(arguments[1]), arguments[2], arguments[3], Path.of(arguments[4]));
        System.out.println("Task worktree prepared: " + worktree);
    }

    /** Executes one bounded, non-repository provider request to prove runner-local credentials work without exposing them. */
    private static void providerHealth(String[] arguments) throws Exception {
        if (arguments.length != 3) throw new IllegalArgumentException("Usage: provider-health <anthropic|openai|gemini|local> <model>");
        ProviderExecutionPolicy policy = new ProviderExecutionPolicy(arguments[1], arguments[2], 1);
        ProviderClient provider = new ProviderClientFactory().create(policy);
        ProviderResult result = provider.execute(new ProviderRequest(arguments[2], "You are a credential health check.", "Reply with exactly: ForgeLoop provider ready.", 128));
        System.out.println("Provider health check passed. request=" + result.providerRequestId() + " inputTokens=" + result.inputTokens() + " outputTokens=" + result.outputTokens());
    }

    /** Generates and commits a schema-validated Claude patch only within operator-supplied policy prefixes. */
    private static void generatePatch(String[] arguments) throws Exception {
        if (arguments.length != 8) throw new IllegalArgumentException("Usage: generate-patch <anthropic|openai|gemini|local> <model> <max-attempts> <worktree> <allowed-prefixes> <title> <specification>");
        ProviderExecutionPolicy policy = new ProviderExecutionPolicy(arguments[1], arguments[2], Integer.parseInt(arguments[3]));
        ProviderClient provider = new ProviderClientFactory().create(policy);
        String instructions = "Return JSON only: {summary:string,changes:[{path:string,content:string,message:string}]}. "
                + "Propose complete file contents only. Do not use paths outside the allowed prefixes.";
        String input = "Task: " + arguments[6] + "\nAllowed prefixes: " + arguments[5] + "\nSpecification:\n" + arguments[7];
        ProviderExecutionResult execution;
        try {
            execution = new ProviderExecutionService().executeDetailed(provider, new ProviderRequest(policy.model(), instructions, input, 8192), policy.maxAttempts());
        } catch (ProviderExecutionFailure failure) {
            ProviderFailureEvidence evidence = ProviderFailureEvidence.from(policy, failure, java.util.UUID.randomUUID().toString());
            System.err.println("Provider execution blocked: category=" + evidence.category() + " retryable=" + evidence.retryable());
            throw failure;
        }
        ProviderResult result = execution.result();
        PatchPlan plan = PatchPlan.parse(result.output());
        Path worktree = Path.of(arguments[4]); List<String> prefixes = List.of(arguments[5].split(","));
        new PatchWriter().apply(worktree, plan, prefixes);
        String sha = new GitWorktreeManager().commit(worktree, "forgeloop: " + plan.summary());
        ProviderUsageEvidence usage = ProviderUsageEvidence.from(policy, execution);
        System.out.println("Validated patch committed: " + sha + " provider=" + usage.provider() + " inputTokens=" + usage.inputTokens() + " outputTokens=" + usage.outputTokens());
    }

    /** Runs the entire authenticated provider-task lifecycle and fails the lease on every unsafe output path. */
    private static void executeProviderTask(String[] arguments) throws Exception {
        if (arguments.length != 11) throw new IllegalArgumentException("Usage: execute-provider-task <control-plane-url> <identity-file> <task-id> <repositories-root> <workspace-root> <anthropic|openai|gemini|local> <model> <max-attempts> <allowed-prefixes> <lease-file>");
        RunnerIdentity identity = new RunnerIdentityStore().load(Path.of(arguments[2]));
        RunnerClient client = new RunnerClient(HttpClient.newHttpClient(), URI.create(arguments[1]));
        RunnerTask task = client.availableTasks(identity).stream().filter(candidate -> candidate.id().equals(arguments[3])).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Task is not available to this runner"));
        ProviderExecutionPolicy policy = new ProviderExecutionPolicy(arguments[6], arguments[7], Integer.parseInt(arguments[8]));
        String policyPrefixes = task.ownedPaths().isEmpty() ? arguments[9] : String.join(",", task.ownedPaths());
        executeProviderTask(arguments[1], arguments[2], task, arguments[4], arguments[5], policy, policyPrefixes, arguments[10]);
    }

    /** Production entry point: provider and model come from a runner-local reviewed policy file, not task input. */
    private static void executePolicyTask(String[] arguments) throws Exception {
        if (arguments.length != 9) throw new IllegalArgumentException("Usage: execute-policy-task <control-plane-url> <identity-file> <task-id> <repositories-root> <workspace-root> <provider-policy-file> <allowed-prefixes> <lease-file>");
        RunnerIdentity identity = new RunnerIdentityStore().load(Path.of(arguments[2]));
        RunnerClient client = new RunnerClient(HttpClient.newHttpClient(), URI.create(arguments[1]));
        RunnerTask task = client.availableTasks(identity).stream().filter(candidate -> candidate.id().equals(arguments[3])).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Task is not available to this runner"));
        if ("VERIFICATION".equals(task.role())) {
            executeVerificationTask(client, identity, task, arguments[4], arguments[5], Path.of(arguments[8]));
            return;
        }
        ProviderExecutionPolicy policy = RunnerProviderPolicy.load(Path.of(arguments[6])).select(task.role());
        if ("INTEGRATION".equals(task.role())) {
            executeIntegrationTask(client, identity, task, arguments[4], arguments[5], Path.of(arguments[8]));
            return;
        }
        if ("PLANNER".equals(task.role())) {
            executePlannerTask(client, identity, task, arguments[4], arguments[5], policy, Path.of(arguments[8]));
            return;
        }
        if ("REVIEW".equals(task.role())) {
            executeReviewTask(client, identity, task, arguments[4], arguments[5], policy, Path.of(arguments[8]));
            return;
        }
        String policyPrefixes = task.ownedPaths().isEmpty() ? arguments[7] : String.join(",", task.ownedPaths());
        executeProviderTask(arguments[1], arguments[2], task, arguments[4], arguments[5], policy, policyPrefixes, arguments[8]);
    }

    /** Executes only the immutable command and container digest selected by the repository policy snapshot. */
    private static void executeVerificationTask(RunnerClient client, RunnerIdentity identity, RunnerTask task,
                                                String repositoriesRoot, String workspaceRoot, Path leaseFile) throws Exception {
        if (task.verificationGateName() == null || task.verificationImageDigest() == null || task.verificationCommand().isEmpty()
                || task.verificationTimeoutSeconds() == null || task.verificationNetworkPolicy() == null) throw new IllegalArgumentException("Verification task policy is incomplete");
        RunnerLease lease = client.claimTask(identity, task.id());
        new RunnerLeaseStore().save(leaseFile, lease);
        Path repository = checkout(client,identity,lease,repositoriesRoot,task.repository());
        Path worktree = new GitWorktreeManager().create(repository, checkoutRef(task,task.verificationBaseRef()), task.id(), Path.of(workspaceRoot));
        client.acknowledgeLease(identity, lease.leaseId(), lease.nonce());
        RunnerEventReporter events=new RunnerEventReporter(client,identity,lease);
        events.info("LEASE_ACKNOWLEDGED","Verification lease acknowledged");
        events.info("EXECUTION_STARTED","Policy verification started");
        try {
            Path evidenceDirectory = Path.of(workspaceRoot).resolve("evidence").resolve(task.id());
            Files.createDirectories(evidenceDirectory);
            VerificationResult result = new ContainerVerificationExecutor().execute(worktree, dockerVisibleWorktree(worktree),
                    evidenceDirectory, dockerVisibleWorktree(evidenceDirectory),
                    task.verificationImageDigest(), task.verificationCommand(), Duration.ofSeconds(task.verificationTimeoutSeconds()),
                    "EGRESS".equals(task.verificationNetworkPolicy()));
            VerificationEvidenceReport localReport = new VerificationEvidenceReport(task.verificationKind(), task.verificationGateName(),
                    task.verificationImageDigest(), task.verificationCommand(), result, null);
            EvidenceBundleWriter writer = new EvidenceBundleWriter();
            Path localArtifact = writer.write(evidenceDirectory, localReport);
            if (!writer.verify(localArtifact)) throw new IllegalStateException("Local evidence checksum verification failed");
            byte[] artifactBytes = Files.readAllBytes(localArtifact);
            String artifactReference = client.uploadArtifact(identity, lease, artifactBytes, EvidenceDigests.sha256(artifactBytes));
            events.info("ARTIFACT_UPLOADED","Checksummed verification artifact uploaded");
            if ("BROWSER".equals(task.verificationKind())) {
                List<ScreenshotEvidenceCollector.Screenshot> screenshots = new ScreenshotEvidenceCollector().collect(evidenceDirectory);
                for (ScreenshotEvidenceCollector.Screenshot screenshot : screenshots) {
                    byte[] content = screenshot.content();
                    client.uploadArtifact(identity, lease, content, EvidenceDigests.sha256(content),
                            "image/png", "SCREENSHOT", screenshot.displayName());
                }
                if (!screenshots.isEmpty()) events.info("SCREENSHOTS_UPLOADED", "Browser screenshot evidence uploaded: " + screenshots.size());
            }
            VerificationEvidenceReport report = new VerificationEvidenceReport(task.verificationKind(), task.verificationGateName(),
                    task.verificationImageDigest(), task.verificationCommand(), result, artifactReference);
            writer.write(evidenceDirectory, report);
            client.recordEvidence(identity, lease, report);
            events.info(result.passed()?"TASK_COMPLETED":"TASK_FAILED",result.passed()?"Policy verification completed":"Policy verification failed");
            client.completeLease(identity, lease.leaseId(), lease.nonce(), result.passed());
            if (!result.passed()) throw new IllegalStateException("Policy verification failed: " + task.verificationGateName());
            System.out.println("Verification task completed: gate=" + task.verificationGateName() + " evidence=" + report.bundleDigest());
        } catch (Exception failure) {
            try { client.completeLease(identity, lease.leaseId(), lease.nonce(), false); } catch (Exception ignored) { /* Original verification failure wins. */ }
            throw failure;
        }
    }

    /** Cherry-picks only dependency commits declared by the validated task graph. */
    private static void executeIntegrationTask(RunnerClient client, RunnerIdentity identity, RunnerTask task,
                                               String repositoriesRoot, String workspaceRoot, Path leaseFile) throws Exception {
        if (task.dependencyChangeShas().isEmpty()) throw new IllegalArgumentException("Integration task has no dependency changes");
        RunnerLease lease = client.claimTask(identity, task.id());
        new RunnerLeaseStore().save(leaseFile, lease);
        Path repository = checkout(client,identity,lease,repositoriesRoot,task.repository());
        Path worktree = new GitWorktreeManager().create(repository, checkoutRef(task,task.executionBaseRef()), task.id(), Path.of(workspaceRoot));
        client.acknowledgeLease(identity, lease.leaseId(), lease.nonce());
        RunnerEventReporter events=new RunnerEventReporter(client,identity,lease);events.info("LEASE_ACKNOWLEDGED","Integration lease acknowledged");events.info("EXECUTION_STARTED","Integration started");
        try {
            String integratedSha = new GitWorktreeManager().integrate(worktree, task.dependencyChangeShas());
            GithubPushGrant push = client.issueGithubPushGrant(identity, lease);
            new GitWorktreeManager().pushIntegrated(worktree, push.repository(), push.branch(), push.expectedHeadSha(), integratedSha, push.token());
            events.info("TASK_COMPLETED","Integrated branch pushed");client.completeGithubPush(identity, lease, integratedSha);
            System.out.println("Integration task completed: commit=" + integratedSha);
        } catch (Exception conflict) {
            client.completeLease(identity, lease.leaseId(), lease.nonce(), false);
            throw conflict;
        }
    }

    /** Executes a non-writing planner under the same authenticated lease and telemetry boundary. */
    private static void executePlannerTask(RunnerClient client, RunnerIdentity identity, RunnerTask task,
                                           String repositoriesRoot,String workspaceRoot, ProviderExecutionPolicy policy, Path leaseFile) throws Exception {
        RunnerLease lease = client.claimTask(identity, task.id());
        new RunnerLeaseStore().save(leaseFile, lease);
        client.acknowledgeLease(identity, lease.leaseId(), lease.nonce());
        RunnerEventReporter events=new RunnerEventReporter(client,identity,lease);events.info("LEASE_ACKNOWLEDGED","Planner lease acknowledged");events.info("EXECUTION_STARTED","Planning started");
        try {
            Path repository = checkout(client,identity,lease,repositoriesRoot,task.repository());
            Path worktree=new GitWorktreeManager().create(repository,checkoutRef(task,task.executionBaseRef()),task.id(),Path.of(workspaceRoot));
            String context = new RepositoryContextBuilder().build(worktree, List.of("README.md", "AGENTS.md"))
                    + collectMcpOrFail(client, identity, lease, task, worktree);
            PlannerResult result = new PlannerWorker().execute(policy, new ProviderClientFactory().create(policy), task, context, lease.leaseId());
            client.recordProviderAttempt(identity, lease, ProviderAttemptReport.succeeded(result.usage()));
            events.info("TASK_COMPLETED","Validated task plan submitted");client.submitTaskPlan(identity, lease, result.plan());
            System.out.println("Planner task completed: tasks=" + result.plan().tasks().size());
        } catch (ProviderExecutionFailure failure) {
            client.recordProviderAttempt(identity, lease, ProviderAttemptReport.failed(ProviderFailureEvidence.from(policy, failure, lease.leaseId())));
            client.completeLease(identity, lease.leaseId(), lease.nonce(), false);
            throw failure;
        } catch (PlannerOutputFailure invalidOutput) {
            client.recordProviderAttempt(identity, lease, ProviderAttemptReport.rejected(invalidOutput.usage(), "INVALID_PLAN_SCHEMA"));
            client.completeLease(identity, lease.leaseId(), lease.nonce(), false);
            throw invalidOutput;
        }
    }

    /** Polls server-authorized work and executes independent eligible tasks concurrently. */
    private static void workLoop(String[] arguments, boolean continuous) throws Exception {
        if (arguments.length != 9) throw new IllegalArgumentException("Usage: serve|work-until-idle <control-plane-url> <identity-file> <repositories-root> <workspace-root> <provider-policy-file> <allowed-prefixes> <state-root> <parallelism>");
        int parallelism = Integer.parseInt(arguments[8]);
        if (parallelism < 1 || parallelism > 16) throw new IllegalArgumentException("Runner parallelism must be between 1 and 16");
        Path stateRoot = Path.of(arguments[7]).toAbsolutePath().normalize();
        Files.createDirectories(stateRoot);
        RunnerIdentity identity = new RunnerIdentityStore().load(Path.of(arguments[2]));
        RunnerClient client = new RunnerClient(HttpClient.newHttpClient(), URI.create(arguments[1]));
        int idlePolls = 0;
        int failedPolls = 0;
        try (var workers = Executors.newFixedThreadPool(parallelism)) {
            while (continuous || idlePolls < 3) {
                List<RunnerTask> available;
                try {
                    client.heartbeat(identity);
                    available = client.availableTasks(identity);
                    failedPolls = 0;
                } catch (Exception unavailable) {
                    failedPolls++;
                    System.err.println("Control-plane poll failed; retrying: " + safeDiagnostic(unavailable.getMessage()));
                    if (!continuous && failedPolls >= 5) throw unavailable;
                    Thread.sleep(2000);
                    continue;
                }
                if (available.isEmpty()) {
                    idlePolls++;
                    Thread.sleep(2000);
                    continue;
                }
                idlePolls = 0;
                List<Future<?>> futures = new ArrayList<>();
                for (RunnerTask task : available.stream().limit(parallelism).toList()) {
                    futures.add(workers.submit(() -> executeDispatchedTask(arguments, stateRoot, task)));
                }
                for (Future<?> future : futures) future.get();
            }
        }
    }

    private static void executeDispatchedTask(String[] arguments, Path stateRoot, RunnerTask task) {
        Path leasePath = stateRoot.resolve(task.id() + ".lease");
        try {
            // A task can be advertised only after its prior lease is inactive, so stale local
            // nonce/worktree state is safe to remove before a crash-recovery attempt.
            Files.deleteIfExists(leasePath);
            cleanupWorktree(task, arguments[3], arguments[4]);
            executePolicyTask(new String[]{"execute-policy-task", arguments[1], arguments[2], task.id(),
                    arguments[3], arguments[4], arguments[5], arguments[6], leasePath.toString()});
        } catch (Exception failure) {
            String detail = failure.getCause() == null ? failure.getMessage() : failure.getCause().getMessage();
            System.err.println("Task execution failed: task=" + task.id() + " type=" + failure.getClass().getSimpleName()
                    + " detail=" + safeDiagnostic(detail));
        } finally {
            try { Files.deleteIfExists(leasePath); }
            catch (Exception cleanup) { System.err.println("Task lease cleanup failed: task=" + task.id()); }
            cleanupWorktree(task, arguments[3], arguments[4]);
        }
    }
    /** Keeps diagnostics actionable without allowing provider output or credentials into runner logs. */
    private static String safeDiagnostic(String detail){if(detail==null||detail.isBlank())return "unavailable";String singleLine=detail.replaceAll("[\\r\\n]+"," ").replaceAll("(?i)(api[_-]?key|authorization|token|secret)\\s*[:=]\\s*\\S+","$1=[REDACTED]");return singleLine.substring(0,Math.min(singleLine.length(),240));}
    private static void cleanupWorktree(RunnerTask task,String repositoriesRoot,String workspaceRoot){try{Path workspace=Path.of(workspaceRoot).toAbsolutePath().normalize().resolve(task.id());if(Files.exists(workspace)){Path repository=new RepositoryWorkspaceResolver().resolve(Path.of(repositoriesRoot),task.repository());new GitWorktreeManager().remove(repository,task.id(),Path.of(workspaceRoot));}}catch(Exception cleanup){System.err.println("Task worktree cleanup failed: task="+task.id());}}

    private static void executeReviewTask(RunnerClient client,RunnerIdentity identity,RunnerTask task,String repositoriesRoot,String workspaceRoot,ProviderExecutionPolicy policy,Path leaseFile)throws Exception{
        if(task.dependencyChangeShas().isEmpty())throw new IllegalArgumentException("Review task has no integrated dependency");
        RunnerLease lease=client.claimTask(identity,task.id());new RunnerLeaseStore().save(leaseFile,lease);Path repository=checkout(client,identity,lease,repositoriesRoot,task.repository());Path worktree=new GitWorktreeManager().create(repository,task.dependencyChangeShas().getLast(),task.id(),Path.of(workspaceRoot));client.acknowledgeLease(identity,lease.leaseId(),lease.nonce());RunnerEventReporter events=new RunnerEventReporter(client,identity,lease);events.info("LEASE_ACKNOWLEDGED","Review lease acknowledged");events.info("EXECUTION_STARTED","Independent review started");
        try{String diff=new GitWorktreeManager().boundedDiff(worktree,task.baseBranch());RunnerTask contextualTask=withAdditionalContext(task,collectMcpOrFail(client,identity,lease,task,worktree));ReviewResult result=new ReviewWorker().execute(policy,new ProviderClientFactory().create(policy),contextualTask,diff,lease.leaseId());client.recordReviewEvidence(identity,lease,result);client.recordProviderAttempt(identity,lease,ProviderAttemptReport.succeeded(result.usage()));events.info(result.approved()?"TASK_COMPLETED":"TASK_FAILED",result.approved()?"Independent review passed":"Independent review rejected the change");client.completeLease(identity,lease.leaseId(),lease.nonce(),result.approved());if(!result.approved())throw new IllegalStateException("Independent review rejected the integrated change: "+result.summary());System.out.println("Independent review passed.");}
        catch(ProviderExecutionFailure failure){client.recordProviderAttempt(identity,lease,ProviderAttemptReport.failed(ProviderFailureEvidence.from(policy,failure,lease.leaseId())));client.completeLease(identity,lease.leaseId(),lease.nonce(),false);throw failure;}
        catch(GuardedPatchFailure invalid){client.recordProviderAttempt(identity,lease,ProviderAttemptReport.rejected(invalid.usage(),invalid.category()));client.completeLease(identity,lease.leaseId(),lease.nonce(),false);throw invalid;}
    }

    private static void executeProviderTask(String controlPlane, String identityFile, RunnerTask task, String repositoriesRoot,
                                            String workspaceRoot, ProviderExecutionPolicy policy, String allowedPrefixes,
                                            String leaseFile) throws Exception {
        if (!GuardedPatchWorker.supports(task.role())) throw new IllegalArgumentException("Task role is not supported by the guarded patch worker");
        RunnerIdentity identity = new RunnerIdentityStore().load(Path.of(identityFile));
        RunnerClient client = new RunnerClient(HttpClient.newHttpClient(), URI.create(controlPlane));
        RunnerLease lease = client.claimTask(identity, task.id());
        new RunnerLeaseStore().save(Path.of(leaseFile), lease);
        Path repository = checkout(client,identity,lease,repositoriesRoot,task.repository());
        Path worktree = new GitWorktreeManager().create(repository, checkoutRef(task,task.executionBaseRef()), task.id(), Path.of(workspaceRoot));
        client.acknowledgeLease(identity, lease.leaseId(), lease.nonce());
        RunnerEventReporter events=new RunnerEventReporter(client,identity,lease);events.info("LEASE_ACKNOWLEDGED","Implementation lease acknowledged");events.info("EXECUTION_STARTED","Guarded implementation started");
        GuardedPatchResult result;
        try {
            RunnerTask contextualTask = withAdditionalContext(task,
                    collectMcpOrFail(client, identity, lease, task, worktree));
            result = new GuardedPatchWorker().execute(policy, new ProviderClientFactory().create(policy), task.role(),
                    task.title(), contextualTask.specification(), worktree, List.of(allowedPrefixes.split(",")), lease.leaseId());
        } catch (ProviderExecutionFailure failure) {
            client.recordProviderAttempt(identity, lease, ProviderAttemptReport.failed(ProviderFailureEvidence.from(policy, failure, lease.leaseId())));
            client.completeLease(identity, lease.leaseId(), lease.nonce(), false);
            throw failure;
        } catch (GuardedPatchFailure unsafeOutput) {
            client.recordProviderAttempt(identity, lease, ProviderAttemptReport.rejected(unsafeOutput.usage(), unsafeOutput.category()));
            client.completeLease(identity, lease.leaseId(), lease.nonce(), false);
            throw unsafeOutput;
        }
        client.recordProviderAttempt(identity, lease, ProviderAttemptReport.succeeded(result.usage()));
        new GitWorktreeManager().pinTaskCommit(worktree, task.id(), result.commitSha());
        events.info("TASK_COMPLETED","Guarded implementation commit created");client.completeProviderWork(identity, lease, result.commitSha());
        System.out.println("Provider task completed: commit=" + result.commitSha());
    }

    /** Claims exactly one server-advertised task, then creates its isolated worktree from a pre-cloned local checkout. */
    private static void claimAndPrepareTask(String[] arguments) throws Exception {
        if (arguments.length != 7) {
            throw new IllegalArgumentException("Usage: claim-and-prepare-task <control-plane-url> <identity-file> <task-id> <lease-file> <repositories-root> <workspace-root>");
        }
        RunnerIdentity identity = new RunnerIdentityStore().load(Path.of(arguments[2]));
        RunnerClient client = new RunnerClient(HttpClient.newHttpClient(), URI.create(arguments[1]));
        RunnerTask task = client.availableTasks(identity).stream().filter(candidate -> candidate.id().equals(arguments[3])).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Task is not available to this runner"));
        RunnerLease lease = client.claimTask(identity, task.id());
        try {
            Path repository = checkout(client,identity,lease,arguments[5],task.repository());
            Path worktree = new GitWorktreeManager().create(repository, checkoutRef(task,task.baseBranch()), task.id(), Path.of(arguments[6]));
            new RunnerLeaseStore().save(Path.of(arguments[4]), lease);
            client.acknowledgeLease(identity, lease.leaseId(), lease.nonce());
            System.out.println("Task worktree prepared: " + worktree);
        } catch (Exception exception) {
            // No nonce is persisted on failure; the control plane will safely expire and repair/requeue the lease.
            throw exception;
        }
    }
    private static Path checkout(RunnerClient client,RunnerIdentity identity,RunnerLease lease,String repositoriesRoot,String expectedRepository)throws Exception{GithubCheckoutGrant grant=client.issueGithubCheckoutGrant(identity,lease);if(!expectedRepository.equals(grant.repository()))throw new IllegalStateException("Checkout grant repository mismatch");return new RepositoryWorkspaceResolver().resolveOrClone(Path.of(repositoriesRoot),grant);}
    private static String checkoutRef(RunnerTask task,String requested){return task.baseBranch().equals(requested)?"refs/remotes/origin/"+task.baseBranch():requested;}

    private static RunnerTask withAdditionalContext(RunnerTask task, String context) {
        if (context == null || context.isBlank()) return task;
        return new RunnerTask(task.id(), task.role(), task.title(), task.repository(), task.baseBranch(), task.sourceRef(),
                task.specification() + context, task.requiredCapability(), task.budgetUsd(), task.ownedPaths(),
                task.dependencyChangeShas(), task.verificationGateName(), task.verificationKind(),
                task.verificationImageDigest(), task.verificationCommand(), task.verificationNetworkPolicy(),
                task.verificationTimeoutSeconds(), task.verificationBaseRef(), task.executionBaseRef(),
                task.acceptanceCriteria(), task.mcpConfigurations());
    }

    private static String collectMcpOrFail(RunnerClient client, RunnerIdentity identity, RunnerLease lease,
                                           RunnerTask task, Path worktree) throws Exception {
        try {
            return new LocalMcpContextClient().collect(task.mcpConfigurations(), worktree);
        } catch (Exception failure) {
            client.completeLease(identity, lease.leaseId(), lease.nonce(), false);
            throw failure;
        }
    }

    private static void removeWorktree(String[] arguments) throws Exception {
        if (arguments.length != 4) throw new IllegalArgumentException("Usage: remove-worktree <repository-path> <task-id> <workspace-root>");
        new GitWorktreeManager().remove(Path.of(arguments[1]), arguments[2], Path.of(arguments[3]));
        System.out.println("Task worktree removed.");
    }

    private static void verify(String[] arguments) throws Exception {
        if (arguments.length < 4) throw new IllegalArgumentException("Usage: verify <worktree-path> <timeout-seconds> <command> [arguments...]");
        long timeoutSeconds = Long.parseLong(arguments[2]);
        VerificationResult result = new VerificationExecutor().execute(Path.of(arguments[1]), Arrays.asList(arguments).subList(3, arguments.length), Duration.ofSeconds(timeoutSeconds));
        System.out.println(result.passed() ? "Verification passed." : "Verification failed or timed out.");
        System.out.print(result.output());
        if (!result.passed()) System.exit(result.timedOut() ? 124 : result.exitCode());
    }

    private static void verifyContainer(String[] arguments) throws Exception {
        if (arguments.length < 6) {
            throw new IllegalArgumentException(
                    "Usage: verify-container <worktree-path> <timeout-seconds> <network:none|allow> <image> <command> [arguments...]");
        }
        boolean allowNetwork = switch (arguments[3]) {
            case "none" -> false;
            case "allow" -> true;
            default -> throw new IllegalArgumentException("Container network policy must be none or allow");
        };
        long timeoutSeconds = Long.parseLong(arguments[2]);
        VerificationResult result = new ContainerVerificationExecutor().execute(
                Path.of(arguments[1]),
                dockerVisibleWorktree(Path.of(arguments[1])),
                arguments[4],
                Arrays.asList(arguments).subList(5, arguments.length),
                Duration.ofSeconds(timeoutSeconds),
                allowNetwork);
        System.out.println(result.passed() ? "Container verification passed." : "Container verification failed or timed out.");
        System.out.print(result.output());
        if (!result.passed()) System.exit(result.timedOut() ? 124 : result.exitCode());
    }

    /** Executes, reports, then leaves lease completion to an explicit policy decision. */
    private static void verifyContainerAndRecord(String[] arguments) throws Exception {
        if (arguments.length < 10) {
            throw new IllegalArgumentException("Usage: verify-container-and-record <control-plane-url> <identity-file> <lease-file> <worktree-path> <timeout-seconds> <network:none|allow> <image> <command> [arguments...]");
        }
        boolean allowNetwork = switch (arguments[6]) {
            case "none" -> false;
            case "allow" -> true;
            default -> throw new IllegalArgumentException("Container network policy must be none or allow");
        };
        Path worktree = Path.of(arguments[4]);
        List<String> command = Arrays.asList(arguments).subList(8, arguments.length);
        VerificationResult result = new ContainerVerificationExecutor().execute(
                worktree, dockerVisibleWorktree(worktree), arguments[7], command, Duration.ofSeconds(Long.parseLong(arguments[5])), allowNetwork);
        RunnerIdentity identity = new RunnerIdentityStore().load(Path.of(arguments[2]));
        RunnerLease lease = new RunnerLeaseStore().load(Path.of(arguments[3]));
        new RunnerClient(HttpClient.newHttpClient(), URI.create(arguments[1])).recordEvidence(identity, lease,
                new VerificationEvidenceReport("CONTAINER", null, arguments[7], String.join(" ", command), result));
        System.out.println(result.passed() ? "Container verification recorded as passed." : "Container verification recorded as failed.");
        System.out.print(result.output());
        if (!result.passed()) System.exit(result.timedOut() ? 124 : result.exitCode());
    }

    /** Runs a named required gate and lets the control plane update its run-level state from the signed lease report. */
    private static void verifyContainerGateAndRecord(String[] arguments) throws Exception {
        if (arguments.length < 11) {
            throw new IllegalArgumentException("Usage: verify-container-gate-and-record <control-plane-url> <identity-file> <lease-file> <gate> <worktree-path> <timeout-seconds> <network:none|allow> <image> <command> [arguments...]");
        }
        boolean allowNetwork = switch (arguments[7]) {
            case "none" -> false;
            case "allow" -> true;
            default -> throw new IllegalArgumentException("Container network policy must be none or allow");
        };
        Path worktree = Path.of(arguments[5]);
        List<String> command = Arrays.asList(arguments).subList(9, arguments.length);
        VerificationResult result = new ContainerVerificationExecutor().execute(
                worktree, dockerVisibleWorktree(worktree), arguments[8], command, Duration.ofSeconds(Long.parseLong(arguments[6])), allowNetwork);
        RunnerIdentity identity = new RunnerIdentityStore().load(Path.of(arguments[2]));
        RunnerLease lease = new RunnerLeaseStore().load(Path.of(arguments[3]));
        new RunnerClient(HttpClient.newHttpClient(), URI.create(arguments[1])).recordEvidence(identity, lease,
                new VerificationEvidenceReport("CONTAINER", arguments[4], arguments[8], String.join(" ", command), result));
        System.out.println(result.passed() ? "Verification gate recorded as passed." : "Verification gate recorded as failed.");
        System.out.print(result.output());
        if (!result.passed()) System.exit(result.timedOut() ? 124 : result.exitCode());
    }

    /** Runs a bounded container check and writes a checksummed local evidence bundle without sending source or credentials remotely. */
    private static void verifyContainerAndBundle(String[] arguments) throws Exception {
        if (arguments.length < 8) {
            throw new IllegalArgumentException("Usage: verify-container-and-bundle <bundle-directory> <worktree-path> <timeout-seconds> <network:none|allow> <image> <command> [arguments...]");
        }
        boolean allowNetwork = switch (arguments[4]) {
            case "none" -> false;
            case "allow" -> true;
            default -> throw new IllegalArgumentException("Container network policy must be none or allow");
        };
        Path worktree = Path.of(arguments[2]);
        List<String> command = Arrays.asList(arguments).subList(6, arguments.length);
        VerificationResult result = new ContainerVerificationExecutor().execute(
                worktree, dockerVisibleWorktree(worktree), arguments[5], command, Duration.ofSeconds(Long.parseLong(arguments[3])), allowNetwork);
        Path artifact = new EvidenceBundleWriter().write(Path.of(arguments[1]),
                new VerificationEvidenceReport("CONTAINER", null, arguments[5], String.join(" ", command), result));
        System.out.println("Verification evidence bundle written: " + artifact.toAbsolutePath().normalize());
        System.out.print(result.output());
        if (!result.passed()) System.exit(result.timedOut() ? 124 : result.exitCode());
    }

    /**
     * Maps the runner-container path to the path understood by the Docker daemon. Both roots must
     * be configured together, which avoids accidentally mounting an arbitrary daemon-host path.
     */
    static Path dockerVisibleWorktree(Path worktree) {
        String runnerRoot = System.getenv("FORGELOOP_RUNNER_WORKSPACE_ROOT");
        String dockerHostRoot = System.getenv("FORGELOOP_DOCKER_HOST_WORKSPACE_ROOT");
        if ((runnerRoot == null || runnerRoot.isBlank()) && (dockerHostRoot == null || dockerHostRoot.isBlank())) {
            return worktree.toAbsolutePath().normalize();
        }
        if (runnerRoot == null || runnerRoot.isBlank() || dockerHostRoot == null || dockerHostRoot.isBlank()) {
            throw new IllegalArgumentException("Both runner and Docker-host workspace roots must be configured together");
        }
        Path normalizedRunnerRoot = Path.of(runnerRoot).toAbsolutePath().normalize();
        Path normalizedWorktree = worktree.toAbsolutePath().normalize();
        if (!normalizedWorktree.startsWith(normalizedRunnerRoot)) {
            throw new IllegalArgumentException("Task worktree is outside the configured runner workspace root");
        }
        return Path.of(dockerHostRoot).toAbsolutePath().normalize().resolve(normalizedRunnerRoot.relativize(normalizedWorktree));
    }

    private static Path statePath() {
        String configured = System.getenv("FORGELOOP_RUNNER_STATE_FILE");
        return configured == null || configured.isBlank() ? Path.of("forgeloop-runner.state") : Path.of(configured);
    }

}
