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
    @Override public void createBranch(long installationId, String repository, String branch, String baseSha) { request(installationId, "POST", "/repos/" + repository + "/git/refs", Map.of("ref", "refs/heads/" + branch, "sha", baseSha)); }
    @Override public String putFile(long installationId, String repository, String branch, GithubChange change) {
        JsonNode response = request(installationId, "PUT", "/repos/" + repository + "/contents/" + change.path(), Map.of("branch", branch, "message", change.message(), "content", Base64.getEncoder().encodeToString(change.content().getBytes(StandardCharsets.UTF_8))));
        return response.path("commit").path("sha").asText();
    }
    @Override public long createCompletedCheck(long installationId, String repository, String headSha, String name, String summary) {
        JsonNode response = request(installationId, "POST", "/repos/" + repository + "/check-runs", Map.of("name", name, "head_sha", headSha, "status", "completed", "conclusion", "success", "output", Map.of("title", name, "summary", summary)));
        return response.path("id").asLong();
    }
    @Override public long createDraftPullRequest(long installationId, String repository, String head, String base, String title, String body) {
        JsonNode response = request(installationId, "POST", "/repos/" + repository + "/pulls", Map.of("title", title, "head", head, "base", base, "body", body, "draft", true));
        return response.path("number").asLong();
    }
    private JsonNode request(long installationId, String method, String path, Object body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + path)).header("Accept", "application/vnd.github+json").header("Authorization", "Bearer " + installationToken(installationId)).method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            HttpResponse<String> response = sendWithRetry(request);
            return json.readTree(response.body());
        } catch (Exception exception) { throw new IllegalStateException("GitHub API request failed", exception); }
    }
    private String installationToken(long installationId) {
        JsonNode response = requestAsApp("POST", "/app/installations/" + installationId + "/access_tokens", Map.of());
        String token = response.path("token").asText(); if (token.isBlank()) throw new IllegalStateException("GitHub installation token was absent"); return token;
    }
    private JsonNode requestAsApp(String method, String path, Object body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + path)).header("Accept", "application/vnd.github+json").header("Authorization", "Bearer " + appJwt()).method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            HttpResponse<String> response = sendWithRetry(request);
            return json.readTree(response.body());
        } catch (Exception exception) { throw new IllegalStateException("GitHub App token request failed", exception); }
    }
    private String appJwt() throws Exception {
        if (appId.isBlank() || privateKey.isBlank()) throw new IllegalStateException("GitHub App ID and PKCS#8 private key are required for delivery");
        long now = Instant.now().getEpochSecond(); String header = part("{\"alg\":\"RS256\",\"typ\":\"JWT\"}"); String claims = part("{\"iat\":" + (now - 30) + ",\"exp\":" + (now + 540) + ",\"iss\":\"" + appId + "\"}");
        Signature signer = Signature.getInstance("SHA256withRSA"); signer.initSign(key()); signer.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII)); return header + "." + claims + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }
    private PrivateKey key() throws Exception { String pem = privateKey.replace("\\n", "\n").replaceAll("-----[^-]+-----", "").replaceAll("\\s", ""); return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem))); }
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
