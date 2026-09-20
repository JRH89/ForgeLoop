package io.forgeloop.runner;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * CLI entry point for explicit, operator-initiated runner registration.
 * Registration tokens are accepted only as arguments and are never written to stdout.
 */
public final class RunnerMain {
    private RunnerMain() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length > 0 && "heartbeat".equals(arguments[0])) {
            heartbeat(arguments);
            return;
        }
        if (arguments.length > 0 && "available-tasks".equals(arguments[0])) {
            availableTasks(arguments);
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
        if (arguments.length > 0 && "prepare-worktree".equals(arguments[0])) {
            prepareWorktree(arguments);
            return;
        }
        if (arguments.length > 0 && "verify".equals(arguments[0])) {
            verify(arguments);
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
        System.out.println(new RunnerClient(HttpClient.newHttpClient(), URI.create(arguments[1])).availableTasks(identity));
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

    private static void prepareWorktree(String[] arguments) throws Exception {
        if (arguments.length != 5) throw new IllegalArgumentException("Usage: prepare-worktree <repository-path> <base-ref> <task-id> <workspace-root>");
        Path worktree = new GitWorktreeManager().create(Path.of(arguments[1]), arguments[2], arguments[3], Path.of(arguments[4]));
        System.out.println("Task worktree prepared: " + worktree);
    }

    private static void verify(String[] arguments) throws Exception {
        if (arguments.length < 4) throw new IllegalArgumentException("Usage: verify <worktree-path> <timeout-seconds> <command> [arguments...]");
        long timeoutSeconds = Long.parseLong(arguments[2]);
        VerificationResult result = new VerificationExecutor().execute(Path.of(arguments[1]), Arrays.asList(arguments).subList(3, arguments.length), Duration.ofSeconds(timeoutSeconds));
        System.out.println(result.passed() ? "Verification passed." : "Verification failed or timed out.");
        System.out.print(result.output());
        if (!result.passed()) System.exit(result.timedOut() ? 124 : result.exitCode());
    }

    private static Path statePath() {
        String configured = System.getenv("FORGELOOP_RUNNER_STATE_FILE");
        return configured == null || configured.isBlank() ? Path.of("forgeloop-runner.state") : Path.of(configured);
    }

}
