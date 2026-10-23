package io.forgeloop.runner;

import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/** Operator-invoked verification commands, separate from lease-based automated execution. */
final class RunnerVerificationCommands {
    private RunnerVerificationCommands() { }
    static void removeWorktree(String[] arguments) throws Exception {
        if (arguments.length != 4) throw new IllegalArgumentException("Usage: remove-worktree <repository-path> <task-id> <workspace-root>");
        new GitWorktreeManager().remove(Path.of(arguments[1]), arguments[2], Path.of(arguments[3]));
        System.out.println("Task worktree removed.");
    }

    static void verify(String[] arguments) throws Exception {
        if (arguments.length < 4) throw new IllegalArgumentException("Usage: verify <worktree-path> <timeout-seconds> <command> [arguments...]");
        long timeoutSeconds = Long.parseLong(arguments[2]);
        VerificationResult result = new VerificationExecutor().execute(Path.of(arguments[1]), Arrays.asList(arguments).subList(3, arguments.length), Duration.ofSeconds(timeoutSeconds));
        System.out.println(result.passed() ? "Verification passed." : "Verification failed or timed out.");
        System.out.print(result.output());
        if (!result.passed()) System.exit(result.timedOut() ? 124 : result.exitCode());
    }

    static void verifyContainer(String[] arguments) throws Exception {
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
                RunnerMain.dockerVisibleWorktree(Path.of(arguments[1])),
                arguments[4],
                Arrays.asList(arguments).subList(5, arguments.length),
                Duration.ofSeconds(timeoutSeconds),
                allowNetwork);
        System.out.println(result.passed() ? "Container verification passed." : "Container verification failed or timed out.");
        System.out.print(result.output());
        if (!result.passed()) System.exit(result.timedOut() ? 124 : result.exitCode());
    }

    /** Executes, reports, then leaves lease completion to an explicit policy decision. */
    static void verifyContainerAndRecord(String[] arguments) throws Exception {
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
                worktree, RunnerMain.dockerVisibleWorktree(worktree), arguments[7], command, Duration.ofSeconds(Long.parseLong(arguments[5])), allowNetwork);
        RunnerIdentity identity = new RunnerIdentityStore().load(Path.of(arguments[2]));
        RunnerLease lease = new RunnerLeaseStore().load(Path.of(arguments[3]));
        new RunnerClient(HttpClient.newHttpClient(), URI.create(arguments[1])).recordEvidence(identity, lease,
                new VerificationEvidenceReport("CONTAINER", null, arguments[7], String.join(" ", command), result));
        System.out.println(result.passed() ? "Container verification recorded as passed." : "Container verification recorded as failed.");
        System.out.print(result.output());
        if (!result.passed()) System.exit(result.timedOut() ? 124 : result.exitCode());
    }

    /** Runs a named required gate and lets the control plane update its run-level state from the signed lease report. */
    static void verifyContainerGateAndRecord(String[] arguments) throws Exception {
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
                worktree, RunnerMain.dockerVisibleWorktree(worktree), arguments[8], command, Duration.ofSeconds(Long.parseLong(arguments[6])), allowNetwork);
        RunnerIdentity identity = new RunnerIdentityStore().load(Path.of(arguments[2]));
        RunnerLease lease = new RunnerLeaseStore().load(Path.of(arguments[3]));
        new RunnerClient(HttpClient.newHttpClient(), URI.create(arguments[1])).recordEvidence(identity, lease,
                new VerificationEvidenceReport("CONTAINER", arguments[4], arguments[8], String.join(" ", command), result));
        System.out.println(result.passed() ? "Verification gate recorded as passed." : "Verification gate recorded as failed.");
        System.out.print(result.output());
        if (!result.passed()) System.exit(result.timedOut() ? 124 : result.exitCode());
    }

    /** Runs a bounded container check and writes a checksummed local evidence bundle without sending source or credentials remotely. */
    static void verifyContainerAndBundle(String[] arguments) throws Exception {
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
                worktree, RunnerMain.dockerVisibleWorktree(worktree), arguments[5], command, Duration.ofSeconds(Long.parseLong(arguments[3])), allowNetwork);
        Path artifact = new EvidenceBundleWriter().write(Path.of(arguments[1]),
                new VerificationEvidenceReport("CONTAINER", null, arguments[5], String.join(" ", command), result));
        System.out.println("Verification evidence bundle written: " + artifact.toAbsolutePath().normalize());
        System.out.print(result.output());
        if (!result.passed()) System.exit(result.timedOut() ? 124 : result.exitCode());
    }

}
