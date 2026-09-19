package io.forgeloop.runner;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
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

    private static Path statePath() {
        String configured = System.getenv("FORGELOOP_RUNNER_STATE_FILE");
        return configured == null || configured.isBlank() ? Path.of("forgeloop-runner.state") : Path.of(configured);
    }

}
