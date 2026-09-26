package io.forgeloop.control.integrations.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.forgeloop.control.application.FeatureRunService;
import io.forgeloop.control.application.FeatureSubmission;
import io.forgeloop.control.domain.GithubDelivery;
import io.forgeloop.control.domain.GithubDeliveryRepository;
import io.forgeloop.control.domain.GithubInstallationRepository;
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
import org.springframework.beans.factory.annotation.Autowired;

/** Accepts only signed GitHub App deliveries and derives intake only from connected installed repositories. */
@RestController
@RequestMapping("/api/github")
public class GithubWebhookController {
    private final GithubWebhookVerifier verifier;
    private final GithubDeliveryRepository deliveries;
    private final FeatureRunService runs;
    private final RepositoryConnectionRepository connections;
    private final GithubInstallationRepository installationOwners;
    private final GithubInstallationRepositorySyncService installations;
    private final GithubAutoMergeService autoMerge;
    private final ObjectMapper json;
    private final String secret;

    @Autowired public GithubWebhookController(GithubWebhookVerifier verifier, GithubDeliveryRepository deliveries, FeatureRunService runs,
                                   RepositoryConnectionRepository connections, GithubInstallationRepository installationOwners,
                                   GithubInstallationRepositorySyncService installations,
                                   GithubAutoMergeService autoMerge, ObjectMapper json, @Value("${forgeloop.github.webhook-secret:}") String secret) {
        this.verifier = verifier;
        this.deliveries = deliveries;
        this.runs = runs;
        this.connections = connections;
        this.installationOwners = installationOwners;
        this.installations = installations;
        this.autoMerge = autoMerge;
        this.json = json;
        this.secret = secret;
    }

    GithubWebhookController(GithubWebhookVerifier verifier, GithubDeliveryRepository deliveries, FeatureRunService runs,
                            RepositoryConnectionRepository connections, GithubInstallationRepository installationOwners,
                            GithubInstallationRepositorySyncService installations, ObjectMapper json, String secret) {
        this(verifier, deliveries, runs, connections, installationOwners, installations, null, json, secret);
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
            if ("installation".equals(event) && "created".equals(payload.path("action").asText())) {
                long installationId = payload.path("installation").path("id").asLong();
                // GitHub can deliver this before its setup-URL callback. The callback synchronizes once
                // ownership is verified, so accepting this delivery avoids an unsafe retry loop.
                if (installationOwners.findByInstallationId(installationId).isPresent()) {
                    installations.synchronizeInstallation(installationId);
                }
            }
            if (autoMerge != null && "pull_request".equals(event) && "closed".equals(payload.path("action").asText())) {
                JsonNode pullRequest = payload.path("pull_request");
                if (pullRequest.path("merged").asBoolean()) {
                    autoMerge.recordMergedPullRequest(payload.path("repository").path("full_name").asText(),
                            pullRequest.path("number").asLong(), payload.path("installation").path("id").asLong(),
                            pullRequest.path("merge_commit_sha").asText());
                }
            }
            if (autoMerge != null && "completed".equals(payload.path("action").asText()) && ("check_run".equals(event) || "check_suite".equals(event))) {
                JsonNode check = payload.path("check_run".equals(event) ? "check_run" : "check_suite");
                autoMerge.reconcile(payload.path("repository").path("full_name").asText(), check.path("head_sha").asText(), payload.path("installation").path("id").asLong());
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid GitHub webhook payload", exception);
        }
        return ResponseEntity.accepted().build();
    }

    private void processIssue(JsonNode root) {
        String action = root.path("action").asText();
        if (!List.of("opened", "labeled", "assigned", "reopened").contains(action)) return;
        String repository = root.path("repository").path("full_name").asText();
        long installationId = root.path("installation").path("id").asLong();
        List<String> labels = root.path("issue").path("labels").findValuesAsText("name");
        RepositoryConnection connection = connections.findByRepository(repository).orElse(null);
        if (connection == null || (installationId > 0 && !connection.isInstalledAs(installationId))
                || labels.stream().noneMatch(connection::acceptsIssueLabel)) return;
        JsonNode issue = root.path("issue");
        if (issue.has("pull_request") || "closed".equals(issue.path("state").asText())) return;
        if (!connection.acceptsAssignees(issue.path("assignees").findValuesAsText("login"))) return;
        String specification = issue.path("body").asText();
        if (specification.isBlank()) return;
        runs.submitIssue(new FeatureSubmission(repository, "issue-" + issue.path("number").asText(), issue.path("title").asText(), specification,
                connection.getMaxBudgetUsd()));
    }
}
