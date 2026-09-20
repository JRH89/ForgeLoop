package io.forgeloop.control.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

  protected RepositoryConnection() { }
  public RepositoryConnection(String organizationId, String repository, long installationId, String defaultBranch, String issueLabel, String harnessProfile, List<String> requiredGates, double maxBudgetUsd) {
    if (organizationId == null || organizationId.isBlank()) throw new IllegalArgumentException("Organization is required");
    this.organizationId = organizationId; this.repository = repository; this.installationId = installationId; this.defaultBranch = defaultBranch; this.issueLabel = issueLabel;
    this.harnessProfile = harnessProfile; this.requiredGates = String.join(",", requiredGates); this.maxBudgetUsd = maxBudgetUsd; this.enabled = true; this.policyRevision = 1;
  }
  public boolean belongsTo(String candidateOrganizationId) { return organizationId.equals(candidateOrganizationId); }
  public boolean acceptsIssueLabel(String label) { return enabled && issueLabel.equals(label); }
  public boolean isInstalledAs(long candidateInstallationId) { return installationId == candidateInstallationId; }
  public boolean permitsBudget(double requestedBudgetUsd) { return enabled && requestedBudgetUsd <= maxBudgetUsd; }
  public void disable() { enabled = false; }
  public String getId() { return id; } public String getOrganizationId() { return organizationId; } public String getRepository() { return repository; } public long getInstallationId() { return installationId; }
  public boolean isEnabled() { return enabled; } public String getDefaultBranch() { return defaultBranch; } public String getIssueLabel() { return issueLabel; }
  public String getHarnessProfile() { return harnessProfile; } public double getMaxBudgetUsd() { return maxBudgetUsd; } public int getPolicyRevision() { return policyRevision; }
  public List<String> getRequiredGates() { return Arrays.stream(requiredGates.split(",")).filter(gate -> !gate.isBlank()).toList(); }
}
