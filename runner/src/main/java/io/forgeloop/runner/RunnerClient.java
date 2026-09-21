package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

/** Minimal GraphQL transport; never writes credentials or source content to stdout. */
public final class RunnerClient {
    private static final Pattern ENROLLMENT = Pattern.compile("(?s)\\\"runner\\\"\\s*:\\s*\\{.*?\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\".*?}.*?\\\"credential\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern LEASE_GRANT = Pattern.compile("(?s)\\\"lease\\\"\\s*:\\s*\\{.*?\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\".*?}.*?\\\"nonce\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final ObjectMapper JSON = new ObjectMapper();
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
    /** Retrieves only tasks the authenticated runner may attempt to claim. */
    /** Parses structured server-derived context rather than trusting a local task description. */
    public List<RunnerTask> availableTasks(RunnerIdentity identity) throws Exception {
        String response = post("query($runnerId:ID!,$credential:String!){availableRunnerTasks(runnerId:$runnerId,credential:$credential){id role title repository baseBranch sourceRef specification requiredCapability}}", "{\"runnerId\":\"" + escape(identity.runnerId()) + "\",\"credential\":\"" + escape(identity.credential()) + "\"}");
        List<RunnerTask> tasks = new ArrayList<>();
        for (JsonNode task : JSON.readTree(response).path("data").path("availableRunnerTasks")) {
            tasks.add(new RunnerTask(task.path("id").asText(), task.path("role").asText(), task.path("title").asText(),
                    task.path("repository").asText(), task.path("baseBranch").asText(), task.path("sourceRef").asText(), task.path("specification").asText(), task.path("requiredCapability").asText()));
        }
        return List.copyOf(tasks);
    }
    public RunnerLease claimTask(RunnerIdentity identity, String taskId) throws Exception { Matcher match = LEASE_GRANT.matcher(post("mutation($taskId:ID!,$runnerId:ID!,$credential:String!){claimTaskLease(taskId:$taskId,runnerId:$runnerId,credential:$credential){lease{id} nonce}}", "{\"taskId\":\"" + escape(taskId) + "\",\"runnerId\":\"" + escape(identity.runnerId()) + "\",\"credential\":\"" + escape(identity.credential()) + "\"}")); if (!match.find()) throw new IllegalStateException("Control-plane lease response was malformed"); return new RunnerLease(match.group(1), match.group(2)); }
    /** Acknowledges a one-time lease nonce with the enrolled runner credential. */
    public String acknowledgeLease(RunnerIdentity identity, String leaseId, String nonce) throws Exception { return post("mutation($leaseId:ID!,$runnerId:ID!,$nonce:String!,$credential:String!){acknowledgeTaskLease(leaseId:$leaseId,runnerId:$runnerId,nonce:$nonce,credential:$credential){id acknowledged}}", "{\"leaseId\":\"" + escape(leaseId) + "\",\"runnerId\":\"" + escape(identity.runnerId()) + "\",\"nonce\":\"" + escape(nonce) + "\",\"credential\":\"" + escape(identity.credential()) + "\"}"); }
    /** Completes an acknowledged lease and records whether its runner verification passed. */
    public String completeLease(RunnerIdentity identity, String leaseId, String nonce, boolean passed) throws Exception { return post("mutation($leaseId:ID!,$runnerId:ID!,$nonce:String!,$credential:String!,$passed:Boolean!){completeTaskLease(leaseId:$leaseId,runnerId:$runnerId,nonce:$nonce,credential:$credential,passed:$passed){id completed}}", "{\"leaseId\":\"" + escape(leaseId) + "\",\"runnerId\":\"" + escape(identity.runnerId()) + "\",\"nonce\":\"" + escape(nonce) + "\",\"credential\":\"" + escape(identity.credential()) + "\",\"passed\":" + passed + "}"); }
    /** Marks agent-authored work ready for independent integration and verification, never verified. */
    public String completeProviderWork(RunnerIdentity identity, RunnerLease lease) throws Exception {
        String variables = "{\"leaseId\":\"" + escape(lease.leaseId()) + "\",\"runnerId\":\"" + escape(identity.runnerId())
                + "\",\"nonce\":\"" + escape(lease.nonce()) + "\",\"credential\":\"" + escape(identity.credential()) + "\"}";
        return post("mutation($leaseId:ID!,$runnerId:ID!,$nonce:String!,$credential:String!){completeProviderTaskLease(leaseId:$leaseId,runnerId:$runnerId,nonce:$nonce,credential:$credential){id completed}}", variables);
    }
    /** Records immutable evidence before a lease is completed, while its nonce is still valid. */
    public String recordEvidence(RunnerIdentity identity, RunnerLease lease, VerificationEvidenceReport report) throws Exception {
        VerificationResult result = report.result();
        String variables = "{\"leaseId\":\"" + escape(lease.leaseId()) + "\",\"runnerId\":\"" + escape(identity.runnerId())
                + "\",\"nonce\":\"" + escape(lease.nonce()) + "\",\"credential\":\"" + escape(identity.credential())
                + "\",\"input\":{\"kind\":\"" + escape(report.kind()) + "\",\"gate\":" + nullable(report.gate()) + ",\"image\":" + nullable(report.image())
                + ",\"command\":\"" + escape(report.command()) + "\",\"exitCode\":" + result.exitCode()
                + ",\"timedOut\":" + result.timedOut() + ",\"output\":\"" + escape(result.output()) + "\"}}";
        return post("mutation($leaseId:ID!,$runnerId:ID!,$nonce:String!,$credential:String!,$input:VerificationEvidenceInput!){recordVerificationEvidence(leaseId:$leaseId,runnerId:$runnerId,nonce:$nonce,credential:$credential,input:$input){id digest}}", variables);
    }
    /** Sends metadata-only provider evidence through the same authenticated lease boundary as verification evidence. */
    public String recordProviderAttempt(RunnerIdentity identity, RunnerLease lease, ProviderAttemptReport report) throws Exception {
        String variables = "{\"leaseId\":\"" + escape(lease.leaseId()) + "\",\"runnerId\":\"" + escape(identity.runnerId())
                + "\",\"nonce\":\"" + escape(lease.nonce()) + "\",\"credential\":\"" + escape(identity.credential())
                + "\",\"input\":{\"provider\":\"" + escape(report.provider()) + "\",\"model\":\"" + escape(report.model())
                + "\",\"requestIdDigest\":\"" + escape(report.requestIdDigest()) + "\",\"inputTokens\":" + report.inputTokens()
                + ",\"outputTokens\":" + report.outputTokens() + ",\"attemptCount\":" + report.attemptCount()
                + ",\"estimatedCostMicros\":" + report.estimatedCostMicros() + ",\"costKnown\":" + report.costKnown()
                + ",\"outcome\":\"" + escape(report.outcome()) + "\",\"retryable\":" + report.retryable()
                + ",\"category\":\"" + escape(report.category()) + "\"}}";
        return post("mutation($leaseId:ID!,$runnerId:ID!,$nonce:String!,$credential:String!,$input:ProviderAttemptInput!){recordProviderAttempt(leaseId:$leaseId,runnerId:$runnerId,nonce:$nonce,credential:$credential,input:$input){id requestIdDigest outcome}}", variables);
    }
    private String post(String query, String variables) throws Exception { String body = "{\"query\":\"" + escape(query) + "\",\"variables\":" + variables + "}"; HttpResponse<String> response = http.send(HttpRequest.newBuilder(endpoint).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString()); if (response.statusCode() != 200 || response.body().contains("\"errors\"")) throw new IllegalStateException("Control-plane request failed"); return response.body(); }
    private static String nullable(String value) { return value == null ? "null" : "\"" + escape(value) + "\""; }
    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\"); case '"' -> escaped.append("\\\""); case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f"); case '\n' -> escaped.append("\\n"); case '\r' -> escaped.append("\\r"); case '\t' -> escaped.append("\\t");
                default -> { if (character < 0x20) escaped.append(String.format("\\u%04x", (int) character)); else escaped.append(character); }
            }
        }
        return escaped.toString();
    }
}
