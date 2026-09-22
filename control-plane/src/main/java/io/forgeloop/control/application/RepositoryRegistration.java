package io.forgeloop.control.application;

import io.forgeloop.control.domain.VerificationPolicySpec;
import java.util.List;

/** Validated operator input for creating an authorized repository connection. */
public record RepositoryRegistration(String repository, long installationId, String defaultBranch, String issueLabel, String harnessProfile, List<String> requiredGates, double maxBudgetUsd, List<VerificationPolicySpec> verificationPolicies) {
  public RepositoryRegistration(String repository, long installationId, String defaultBranch, String issueLabel, String harnessProfile, List<String> requiredGates, double maxBudgetUsd) { this(repository, installationId, defaultBranch, issueLabel, harnessProfile, requiredGates, maxBudgetUsd, List.of()); }
  public RepositoryRegistration {
    if (repository == null || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("Repository must be owner/name");
    if (installationId <= 0) throw new IllegalArgumentException("GitHub installation id must be positive");
    if (defaultBranch == null || defaultBranch.isBlank() || issueLabel == null || issueLabel.isBlank() || harnessProfile == null || harnessProfile.isBlank()) throw new IllegalArgumentException("Branch, issue label, and harness profile are required");
    requiredGates = requiredGates == null ? List.of() : List.copyOf(requiredGates);
    if (requiredGates.isEmpty() || requiredGates.stream().anyMatch(gate -> gate == null || !gate.matches("[a-z0-9-]+"))) throw new IllegalArgumentException("At least one lowercase verification gate is required");
    if (maxBudgetUsd <= 0 || maxBudgetUsd > 500) throw new IllegalArgumentException("Maximum budget must be between 0 and 500 USD");
    verificationPolicies = verificationPolicies == null ? List.of() : List.copyOf(verificationPolicies);
  }
}
