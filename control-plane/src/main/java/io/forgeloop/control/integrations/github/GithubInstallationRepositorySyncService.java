package io.forgeloop.control.integrations.github;

import com.fasterxml.jackson.databind.JsonNode;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import io.forgeloop.control.domain.GithubInstallationRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Converts signed GitHub App installation repository events into conservative repository connections. */
@Service
public class GithubInstallationRepositorySyncService {
    private final RepositoryConnectionRepository connections;
    private final GithubInstallationRepository installations;
    private final GithubApi github;
    private final String issueLabel;
    private final String harnessProfile;
    private final List<String> requiredGates;
    private final double maxBudgetUsd;

    public GithubInstallationRepositorySyncService(
            RepositoryConnectionRepository connections,
            GithubInstallationRepository installations,
            GithubApi github,
            @Value("${forgeloop.github.default-policy.issue-label:forgeloop}") String issueLabel,
            @Value("${forgeloop.github.default-policy.harness-profile:GENERIC}") String harnessProfile,
            @Value("${forgeloop.github.default-policy.required-gates:unit}") String requiredGates,
            @Value("${forgeloop.github.default-policy.max-budget-usd:25}") double maxBudgetUsd) {
        this.connections = connections;
        this.installations = installations;
        this.github = github;
        this.issueLabel = require(issueLabel, "issue label");
        this.harnessProfile = require(harnessProfile, "harness profile");
        this.requiredGates = Arrays.stream(requiredGates.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList();
        if (this.requiredGates.isEmpty()) throw new IllegalArgumentException("At least one default verification gate is required");
        if (maxBudgetUsd <= 0) throw new IllegalArgumentException("Default repository budget must be positive");
        this.maxBudgetUsd = maxBudgetUsd;
    }

    @Transactional
    public void synchronizeAddedRepositories(JsonNode payload) {
        long installationId = payload.path("installation").path("id").asLong();
        if (installationId <= 0) throw new IllegalArgumentException("GitHub installation id is required");
        for (JsonNode repository : payload.path("repositories_added")) synchronize(repository, installationId);
    }
    /** Covers first-time installations, for which GitHub can send only an installation event. */
    @Transactional
    public void synchronizeInstallation(long installationId) {
        if (installationId <= 0) throw new IllegalArgumentException("GitHub installation id is required");
        for (GithubInstalledRepository repository : github.listInstallationRepositories(installationId)) synchronize(repository.fullName(), repository.defaultBranch(), installationId);
    }

    private void synchronize(JsonNode repository, long installationId) {
        synchronize(require(repository.path("full_name").asText(), "repository full name"), repository.path("default_branch").asText("main"), installationId);
    }
    private void synchronize(String fullName, String defaultBranch, long installationId) {
        fullName = require(fullName, "repository full name");
        String organizationId = installations.findByInstallationId(installationId).orElseThrow(() -> new IllegalArgumentException("GitHub installation has no verified organization owner")).getOrganizationId();
        RepositoryConnection existing = connections.findByRepository(fullName).orElse(null);
        if (existing != null) {
            if (!existing.belongsTo(organizationId)) throw new IllegalStateException("GitHub repository is already owned by another ForgeLoop organization");
            if (!existing.isInstalledAs(installationId)) { existing.reconcileInstallation(installationId); connections.save(existing); }
            return;
        }
        connections.save(new RepositoryConnection(organizationId, fullName, installationId, defaultBranch, issueLabel, harnessProfile, requiredGates, maxBudgetUsd));
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("GitHub " + field + " is required");
        return value;
    }
}
