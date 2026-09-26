package io.forgeloop.control.integrations.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** GitHub REST adapter. Tokens are short-lived and only held for the individual API request. */
@Component
public class GithubHttpApi implements GithubApi {
    private final String apiUrl, appId, privateKey;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newHttpClient();
    public GithubHttpApi(@Value("${forgeloop.github.api-url:https://api.github.com}") String apiUrl,
                         @Value("${forgeloop.github.app-id:}") String appId,
                         @Value("${forgeloop.github.private-key:}") String privateKey, ObjectMapper json) {
        this.apiUrl = apiUrl.replaceAll("/$", ""); this.appId = appId; this.privateKey = privateKey; this.json = json;
    }
    @Override public List<GithubInstalledRepository> listInstallationRepositories(long installationId) {
        JsonNode response = request(installationId, "GET", "/installation/repositories", Map.of());
        java.util.ArrayList<GithubInstalledRepository> repositories = new java.util.ArrayList<>();
        for (JsonNode repository : response.path("repositories")) repositories.add(new GithubInstalledRepository(repository.path("full_name").asText(), repository.path("default_branch").asText("main")));
        return List.copyOf(repositories);
    }
    @Override public String issueInstallationToken(long installationId) {
        JsonNode response = requestAsApp("POST", "/app/installations/" + installationId + "/access_tokens", Map.of());
        String token = response.path("token").asText(); if (token.isBlank()) throw new IllegalStateException("GitHub installation token was absent"); return token;
    }
    @Override public String getBranchHead(long installationId, String repository, String branch) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + "/repos/" + repository + "/git/ref/heads/" + branch))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + issueInstallationToken(installationId)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return null;
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("GitHub API request failed with HTTP " + response.statusCode());
            return json.readTree(response.body()).path("object").path("sha").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("GitHub branch lookup failed", exception);
        }
    }
    @Override public long createCompletedCheck(long installationId, String repository, String headSha, String name, String summary) {
        JsonNode response = request(installationId, "POST", "/repos/" + repository + "/check-runs", Map.of("name", name, "head_sha", headSha, "status", "completed", "conclusion", "success", "output", Map.of("title", name, "summary", summary)));
        return response.path("id").asLong();
    }
    @Override public long createPullRequest(long installationId, String repository, String head, String base, String title, String body, boolean draft) {
        JsonNode response = request(installationId, "POST", "/repos/" + repository + "/pulls", Map.of("title", title, "head", head, "base", base, "body", body, "draft", draft));
        return response.path("number").asLong();
    }
    @Override public boolean checksPass(long installationId, String repository, String headSha) {
        JsonNode checks = request(installationId, "GET", "/repos/" + repository + "/commits/" + headSha + "/check-runs?filter=latest&per_page=100", Map.of());
        if (checks.path("total_count").asInt() == 0) return false;
        for (JsonNode check : checks.path("check_runs")) {
            if (!"completed".equals(check.path("status").asText()) || !List.of("success", "neutral", "skipped").contains(check.path("conclusion").asText())) return false;
        }
        JsonNode statuses = request(installationId, "GET", "/repos/" + repository + "/commits/" + headSha + "/status", Map.of());
        return statuses.path("statuses").isEmpty() || "success".equals(statuses.path("state").asText());
    }
    @Override public String getPullRequestHead(long installationId, String repository, long pullRequestNumber) {
        return request(installationId, "GET", "/repos/" + repository + "/pulls/" + pullRequestNumber, Map.of()).path("head").path("sha").asText();
    }
    @Override public String getPullRequestState(long installationId, String repository, long pullRequestNumber) {
        JsonNode pullRequest = request(installationId, "GET", "/repos/" + repository + "/pulls/" + pullRequestNumber, Map.of());
        if (pullRequest.path("merged").asBoolean()) return "MERGED";
        String state = pullRequest.path("state").asText("").toUpperCase(java.util.Locale.ROOT);
        if ("OPEN".equals(state) && pullRequest.path("draft").asBoolean()) return "DRAFT";
        return List.of("OPEN", "CLOSED").contains(state) ? state : "UNKNOWN";
    }
    @Override public String mergePullRequest(long installationId, String repository, long pullRequestNumber, String expectedHeadSha) {
        JsonNode response = request(installationId, "PUT", "/repos/" + repository + "/pulls/" + pullRequestNumber + "/merge", Map.of("sha", expectedHeadSha, "merge_method", "squash"));
        if (!response.path("merged").asBoolean()) throw new IllegalStateException("GitHub declined the pull request merge");
        String sha = response.path("sha").asText();
        if (sha.isBlank()) throw new IllegalStateException("GitHub merge receipt did not contain a commit SHA");
        return sha;
    }
    private JsonNode request(long installationId, String method, String path, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(apiUrl + path)).header("Accept", "application/vnd.github+json").header("Authorization", "Bearer " + issueInstallationToken(installationId));
            HttpRequest request = "GET".equals(method) ? builder.GET().build() : builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            HttpResponse<String> response = sendWithRetry(request);
            return json.readTree(response.body());
        } catch (Exception exception) { throw new IllegalStateException("GitHub API request failed", exception); }
    }
    private JsonNode requestAsApp(String method, String path, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(apiUrl + path)).header("Accept", "application/vnd.github+json").header("Authorization", "Bearer " + appJwt());
            HttpRequest request = "GET".equals(method) ? builder.GET().build() : builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            HttpResponse<String> response = sendWithRetry(request);
            return json.readTree(response.body());
        } catch (Exception exception) { throw new IllegalStateException("GitHub App token request failed", exception); }
    }
    private String appJwt() throws Exception {
        if (appId.isBlank() || privateKey.isBlank()) throw new IllegalStateException("GitHub App ID and PKCS#8 private key are required for delivery");
        long now = Instant.now().getEpochSecond(); String header = part("{\"alg\":\"RS256\",\"typ\":\"JWT\"}"); String claims = part("{\"iat\":" + (now - 30) + ",\"exp\":" + (now + 540) + ",\"iss\":\"" + appId + "\"}");
        Signature signer = Signature.getInstance("SHA256withRSA"); signer.initSign(key()); signer.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII)); return header + "." + claims + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }
    /** GitHub downloads PKCS#1 RSA keys; PKCS#8 keys supplied by a secret manager also work. */
    private PrivateKey key() throws Exception {
        boolean pkcs1 = privateKey.contains("BEGIN RSA PRIVATE KEY");
        String pem = privateKey.replace("\\n", "\n").replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
        byte[] encoded = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs1 ? pkcs8(encoded) : encoded));
    }
    /** Wraps the PKCS#1 RSA private-key sequence in the standard PKCS#8 rsaEncryption envelope. */
    private static byte[] pkcs8(byte[] pkcs1) {
        byte[] algorithm = new byte[] { 0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00 };
        byte[] version = new byte[] { 0x02, 0x01, 0x00 };
        byte[] octets = der(0x04, pkcs1);
        byte[] body = new byte[version.length + algorithm.length + octets.length];
        System.arraycopy(version, 0, body, 0, version.length); System.arraycopy(algorithm, 0, body, version.length, algorithm.length); System.arraycopy(octets, 0, body, version.length + algorithm.length, octets.length);
        return der(0x30, body);
    }
    private static byte[] der(int tag, byte[] value) {
        if (value.length < 128) { byte[] output = new byte[2 + value.length]; output[0] = (byte) tag; output[1] = (byte) value.length; System.arraycopy(value, 0, output, 2, value.length); return output; }
        int lengthBytes = value.length < 256 ? 1 : 2;
        byte[] output = new byte[2 + lengthBytes + value.length]; output[0] = (byte) tag; output[1] = (byte) (0x80 | lengthBytes);
        for (int index = 0; index < lengthBytes; index++) output[2 + index] = (byte) (value.length >>> (8 * (lengthBytes - index - 1)));
        System.arraycopy(value, 0, output, 2 + lengthBytes, value.length); return output;
    }
    /** Retries only transient GitHub failures; permanent 4xx responses remain visible to reconciliation. */
    private HttpResponse<String> sendWithRetry(HttpRequest request) throws Exception {
        HttpResponse<String> response = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) return response;
            if (response.statusCode() != 429 && response.statusCode() < 500) break;
            Thread.sleep(100L * (attempt + 1));
        }
        throw new IllegalStateException("GitHub API request failed with HTTP " + response.statusCode());
    }
    private static String part(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
}
