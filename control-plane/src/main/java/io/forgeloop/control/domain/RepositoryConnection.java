package io.forgeloop.control.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** An enabled GitHub repository scoped to exactly one ForgeLoop organization. */
@Entity
@Table(name = "repository_connection")
public class RepositoryConnection {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
  @Column(nullable = false, unique = true) private String repository;
  @Column(nullable = false) private String organizationId;
  @Column(nullable = false) private long installationId;
  @Column(nullable = false) private boolean enabled;
  @Column(nullable = false) private String defaultBranch;
  @Column(nullable = false) private String issueLabel;
  @Column(nullable = false) private String harnessProfile;
  @Column(nullable = false, length = 2000) private String requiredGates;
  @Column(nullable = false) private double maxBudgetUsd;
  @Column(nullable = false) private int policyRevision;
  @OneToMany(mappedBy = "connection", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<RepositoryVerificationPolicy> verificationPolicies = new ArrayList<>();

  protected RepositoryConnection() { }
  public RepositoryConnection(String organizationId, String repository, long installationId, String defaultBranch, String issueLabel, String harnessProfile, List<String> requiredGates, double maxBudgetUsd) {
    if (organizationId == null || organizationId.isBlank()) throw new IllegalArgumentException("Organization is required");
    this.organizationId = organizationId; this.repository = repository; this.installationId = installationId; this.defaultBranch = defaultBranch; this.issueLabel = issueLabel;
    this.harnessProfile = harnessProfile; this.requiredGates = String.join(",", requiredGates); this.maxBudgetUsd = maxBudgetUsd; this.enabled = true; this.policyRevision = 1;
  }
  public RepositoryConnection(String organizationId, String repository, long installationId, String defaultBranch, String issueLabel,
                              String harnessProfile, List<VerificationPolicySpec> verificationPolicies, double maxBudgetUsd, boolean detailedPolicy) {
    if (organizationId == null || organizationId.isBlank()) throw new IllegalArgumentException("Organization is required");
    if (verificationPolicies == null || verificationPolicies.isEmpty()) throw new IllegalArgumentException("At least one verification policy is required");
    if (verificationPolicies.stream().map(VerificationPolicySpec::name).distinct().count() != verificationPolicies.size()) throw new IllegalArgumentException("Verification gate names must be unique");
    this.organizationId = organizationId; this.repository = repository; this.installationId = installationId; this.defaultBranch = defaultBranch; this.issueLabel = issueLabel;
    this.harnessProfile = harnessProfile; this.requiredGates = verificationPolicies.stream().filter(VerificationPolicySpec::required).map(VerificationPolicySpec::name).reduce((a,b) -> a + "," + b).orElse("");
    this.maxBudgetUsd = maxBudgetUsd; this.enabled = true; this.policyRevision = 1;
    verificationPolicies.forEach(spec -> this.verificationPolicies.add(new RepositoryVerificationPolicy(this, spec)));
  }
  public boolean belongsTo(String candidateOrganizationId) { return organizationId.equals(candidateOrganizationId); }
  public boolean acceptsIssueLabel(String label) { return enabled && issueLabel.equals(label); }
  public boolean isInstalledAs(long candidateInstallationId) { return installationId == candidateInstallationId; }
  /** Rebinds a pre-existing policy to its verified GitHub App installation without changing the policy itself. */
  public void reconcileInstallation(long verifiedInstallationId) { if (verifiedInstallationId <= 0) throw new IllegalArgumentException("GitHub installation id must be positive"); installationId = verifiedInstallationId; }
  public boolean permitsBudget(double requestedBudgetUsd) { return enabled && requestedBudgetUsd <= maxBudgetUsd; }
  public void disable() { enabled = false; }
  public void replaceVerificationPolicies(List<VerificationPolicySpec> policies) {
    if (policies == null || policies.isEmpty()) throw new IllegalArgumentException("At least one verification policy is required");
    if (policies.stream().map(VerificationPolicySpec::name).distinct().count() != policies.size()) throw new IllegalArgumentException("Verification gate names must be unique");
    java.util.Set<String> names = policies.stream().map(VerificationPolicySpec::name).collect(java.util.stream.Collectors.toSet());
    verificationPolicies.removeIf(existing -> !names.contains(existing.toSpec().name()));
    policies.forEach(spec -> verificationPolicies.stream().filter(existing -> existing.matches(spec.name())).findFirst()
            .ifPresentOrElse(existing -> existing.apply(spec), () -> verificationPolicies.add(new RepositoryVerificationPolicy(this, spec))));
    requiredGates = policies.stream().filter(VerificationPolicySpec::required).map(VerificationPolicySpec::name).reduce((a,b) -> a + "," + b).orElse(""); policyRevision++;
  }
  public String getId() { return id; } public String getOrganizationId() { return organizationId; } public String getRepository() { return repository; } public long getInstallationId() { return installationId; }
  public boolean isEnabled() { return enabled; } public String getDefaultBranch() { return defaultBranch; } public String getIssueLabel() { return issueLabel; }
  public String getHarnessProfile() { return harnessProfile; } public double getMaxBudgetUsd() { return maxBudgetUsd; } public int getPolicyRevision() { return policyRevision; }
  public List<String> getRequiredGates() { return Arrays.stream(requiredGates.split(",")).filter(gate -> !gate.isBlank()).toList(); }
  public List<VerificationPolicySpec> getVerificationPolicies() { return verificationPolicies.stream().map(RepositoryVerificationPolicy::toSpec).toList(); }
}
