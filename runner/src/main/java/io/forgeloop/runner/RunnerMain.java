package io.forgeloop.runner;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;

/**
 * CLI entry point for explicit, operator-initiated runner registration.
 * Registration tokens are accepted only as arguments and are never written to stdout.
 */
public final class RunnerMain {
    private RunnerMain() {
    }

    public static void main(String[] arguments) throws Exception {
        RunnerConfig config = configFrom(arguments);
        String result = new RunnerClient(HttpClient.newHttpClient(), config.controlPlane()).register(config);
        System.out.println("Runner registered: " + runnerIdFrom(result));
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

    static String runnerIdFrom(String response) {
        return response.replaceAll("(?s).*\\\"id\\\":\\\"([^\\\"]+).*", "$1");
    }
}
