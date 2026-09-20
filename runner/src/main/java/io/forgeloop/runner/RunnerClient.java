package io.forgeloop.runner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal GraphQL transport; never writes credentials or source content to stdout. */
public final class RunnerClient {
    private static final Pattern ENROLLMENT = Pattern.compile("(?s)\\\"runner\\\"\\s*:\\s*\\{.*?\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\".*?}.*?\\\"credential\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private final HttpClient http;
    private final URI endpoint;

    public RunnerClient(HttpClient http, URI controlPlane) { this.http = http; this.endpoint = controlPlane.resolve("/graphql"); }

    public RunnerIdentity register(RunnerConfig config) throws Exception {
        String query = "mutation($input:RegisterRunnerInput!){registerRunner(input:$input){runner{id} credential}}";
        String variables = "{\"input\":{\"token\":\"" + escape(config.registrationToken()) + "\",\"name\":\"" + escape(config.name()) + "\",\"version\":\"" + escape(config.version()) + "\",\"capabilities\":[" + config.capabilities().stream().map(value -> "\"" + escape(value) + "\"").reduce((a, b) -> a + "," + b).orElse("") + "]}}";
        Matcher match = ENROLLMENT.matcher(post(query, variables));
        if (!match.find()) throw new IllegalStateException("Control-plane enrollment response was malformed");
        return new RunnerIdentity(match.group(1), match.group(2));
    }

    public String heartbeat(RunnerIdentity identity) throws Exception { return post("mutation($runnerId:ID!,$credential:String!){runnerHeartbeat(runnerId:$runnerId,credential:$credential){id lastHeartbeatAt}}", "{\"runnerId\":\"" + escape(identity.runnerId()) + "\",\"credential\":\"" + escape(identity.credential()) + "\"}"); }
    /** Acknowledges a one-time lease nonce with the enrolled runner credential. */
    public String acknowledgeLease(RunnerIdentity identity, String leaseId, String nonce) throws Exception { return post("mutation($leaseId:ID!,$runnerId:ID!,$nonce:String!,$credential:String!){acknowledgeTaskLease(leaseId:$leaseId,runnerId:$runnerId,nonce:$nonce,credential:$credential){id acknowledged}}", "{\"leaseId\":\"" + escape(leaseId) + "\",\"runnerId\":\"" + escape(identity.runnerId()) + "\",\"nonce\":\"" + escape(nonce) + "\",\"credential\":\"" + escape(identity.credential()) + "\"}"); }
    /** Completes an acknowledged lease and records whether its runner verification passed. */
    public String completeLease(RunnerIdentity identity, String leaseId, String nonce, boolean passed) throws Exception { return post("mutation($leaseId:ID!,$runnerId:ID!,$nonce:String!,$credential:String!,$passed:Boolean!){completeTaskLease(leaseId:$leaseId,runnerId:$runnerId,nonce:$nonce,credential:$credential,passed:$passed){id completed}}", "{\"leaseId\":\"" + escape(leaseId) + "\",\"runnerId\":\"" + escape(identity.runnerId()) + "\",\"nonce\":\"" + escape(nonce) + "\",\"credential\":\"" + escape(identity.credential()) + "\",\"passed\":" + passed + "}"); }
    private String post(String query, String variables) throws Exception { String body = "{\"query\":\"" + escape(query) + "\",\"variables\":" + variables + "}"; HttpResponse<String> response = http.send(HttpRequest.newBuilder(endpoint).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString()); if (response.statusCode() != 200 || response.body().contains("\"errors\"")) throw new IllegalStateException("Control-plane request failed"); return response.body(); }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
