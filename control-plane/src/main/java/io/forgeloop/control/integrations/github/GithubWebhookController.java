package io.forgeloop.control.integrations.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.forgeloop.control.application.FeatureRunService;
import io.forgeloop.control.application.FeatureSubmission;
import io.forgeloop.control.domain.GithubDelivery;
import io.forgeloop.control.domain.GithubDeliveryRepository;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Accepts only signed GitHub App deliveries and derives intake only from connected installed repositories. */
@RestController
@RequestMapping("/api/github")
public class GithubWebhookController {
    private final GithubWebhookVerifier verifier;
    private final GithubDeliveryRepository deliveries;
    private final FeatureRunService runs;
    private final RepositoryConnectionRepository connections;
    private final GithubInstallationRepositorySyncService installations;
    private final ObjectMapper json;
    private final String secret;

    public GithubWebhookController(GithubWebhookVerifier verifier, GithubDeliveryRepository deliveries, FeatureRunService runs,
                                   RepositoryConnectionRepository connections, GithubInstallationRepositorySyncService installations,
                                   ObjectMapper json, @Value("${forgeloop.github.webhook-secret:}") String secret) {
        this.verifier = verifier;
        this.deliveries = deliveries;
        this.runs = runs;
        this.connections = connections;
        this.installations = installations;
        this.json = json;
        this.secret = secret;
    }

    @PostMapping("/webhooks")
    public ResponseEntity<Void> receive(@RequestHeader("X-GitHub-Delivery") String delivery,
                                        @RequestHeader("X-GitHub-Event") String event,
                                        @RequestHeader("X-Hub-Signature-256") String signature,
                                        @RequestBody String body) {
        if (!verifier.valid(secret, signature, body)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (deliveries.existsByDeliveryId(delivery)) return ResponseEntity.accepted().build();
        deliveries.save(new GithubDelivery(delivery, event));
        try {
            JsonNode payload = json.readTree(body);
            if ("issues".equals(event)) processIssue(payload);
            if ("installation_repositories".equals(event) && "added".equals(payload.path("action").asText())) {
                installations.synchronizeAddedRepositories(payload);
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid GitHub webhook payload", exception);
        }
        return ResponseEntity.accepted().build();
    }

    private void processIssue(JsonNode root) {
        String action = root.path("action").asText();
        if (!"opened".equals(action) && !"labeled".equals(action)) return;
        String repository = root.path("repository").path("full_name").asText();
        long installationId = root.path("installation").path("id").asLong();
        List<String> labels = root.path("issue").path("labels").findValuesAsText("name");
        RepositoryConnection connection = connections.findByRepository(repository).orElse(null);
        if (connection == null || (installationId > 0 && !connection.isInstalledAs(installationId))
                || labels.stream().noneMatch(connection::acceptsIssueLabel)) return;
        JsonNode issue = root.path("issue");
        String specification = issue.path("body").asText();
        if (specification.isBlank()) return;
        runs.submitIssue(new FeatureSubmission(repository, "issue-" + issue.path("number").asText(), issue.path("title").asText(), specification,
                connection.getMaxBudgetUsd()));
    }
}
