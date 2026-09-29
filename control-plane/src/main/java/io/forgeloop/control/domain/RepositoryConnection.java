package io.forgeloop.control.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Embedded;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
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
  private String requiredAssignee;
  @Column(nullable = false) private boolean requireAssignee;
  @Embedded
  @AttributeOverrides({
      @AttributeOverride(name = "maxToolCalls", column = @Column(name = "agent_loop_max_tool_calls")),
      @AttributeOverride(name = "maxTokens", column = @Column(name = "agent_loop_max_tokens")),
      @AttributeOverride(name = "maxWallSeconds", column = @Column(name = "agent_loop_max_wall_seconds")),
      @AttributeOverride(name = "maxConversationBytes", column = @Column(name = "agent_loop_max_conversation_bytes"))
  })
  private AgentLoopBudget agentLoopBudget;
  @Column(length = 80) private String testFirstGate;
  @Column(length = 8000) private String testPathGlobs;
  public String getRequiredAssignee() { return requiredAssignee; }
  public boolean isRequireAssignee() { return requireAssignee || requiredAssignee != null; }
  /** Null disables assignment gating; usernames are compared case-insensitively. */
  public void configureRequiredAssignee(String login) {
    String normalized = login == null ? "" : login.trim();
    configureAssignmentPolicy(!normalized.isEmpty(), normalized);
  }
  public boolean acceptsAssignees(List<String> logins) {
    if (!isRequireAssignee()) return true;
    if (requiredAssignee != null) return logins.stream().anyMatch(requiredAssignee::equalsIgnoreCase);
    return logins != null && !logins.isEmpty();
  }
  /** Requires any GitHub assignment, optionally constrained to one exact login. */
  public void configureAssignmentPolicy(boolean required, String login) {
    String normalized = login == null ? "" : login.trim();
    if (!normalized.isEmpty() && !normalized.matches("[A-Za-z0-9][A-Za-z0-9\\-]{0,38}(\\[bot\\])?"))
      throw new IllegalArgumentException("Enter a GitHub assignee login, without @");
    requireAssignee = required;
    requiredAssignee = required && !normalized.isEmpty() ? normalized : null;
    policyRevision++;
  }
  /** Null disables loop dispatch; every mutation advances the repository policy revision. */
  public void configureAgentLoop(AgentLoopBudget budget) {
    agentLoopBudget = budget == null ? null : budget.copy();
    policyRevision++;
  }
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
    if (testFirstGate != null && policies.stream().noneMatch(policy -> policy.name().equals(testFirstGate)
            && policy.required() && "JUNIT_XML".equals(policy.testReport()))) {
      throw new IllegalStateException("The test-first gate cannot be removed or weakened while test-first is on");
    }
    verificationPolicies.removeIf(existing -> !names.contains(existing.toSpec().name()));
    policies.forEach(spec -> verificationPolicies.stream().filter(existing -> existing.matches(spec.name())).findFirst()
            .ifPresentOrElse(existing -> existing.apply(spec), () -> verificationPolicies.add(new RepositoryVerificationPolicy(this, spec))));
    requiredGates = policies.stream().filter(VerificationPolicySpec::required).map(VerificationPolicySpec::name).reduce((a,b) -> a + "," + b).orElse(""); policyRevision++;
  }
  /** Enables the repository's test-first contract only against a required, report-producing gate. */
  public void configureTestFirst(String gateName, List<String> globs) {
    if (gateName == null) {
      testFirstGate = null;
      testPathGlobs = null;
      policyRevision++;
      return;
    }
    List<String> normalized = globs == null ? List.of() : List.copyOf(globs);
    if (!TestPathGlobs.areValid(normalized) || normalized.stream().distinct().count() != normalized.size()) {
      throw new IllegalArgumentException("Test path globs are invalid");
    }
    VerificationPolicySpec gate = verificationPolicies.stream().map(RepositoryVerificationPolicy::toSpec)
            .filter(policy -> policy.name().equals(gateName)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Test-first gate must be a required policy that declares a test report"));
    if (!gate.required() || gate.testReport() == null) {
      throw new IllegalArgumentException("Test-first gate must be a required policy that declares a test report");
    }
    testFirstGate = gateName;
    testPathGlobs = String.join("\n", normalized);
    policyRevision++;
  }
  public String getId() { return id; } public String getOrganizationId() { return organizationId; } public String getRepository() { return repository; } public long getInstallationId() { return installationId; }
  public boolean isEnabled() { return enabled; } public String getDefaultBranch() { return defaultBranch; } public String getIssueLabel() { return issueLabel; }
  public String getHarnessProfile() { return harnessProfile; } public double getMaxBudgetUsd() { return maxBudgetUsd; } public int getPolicyRevision() { return policyRevision; }
  public List<String> getRequiredGates() { return Arrays.stream(requiredGates.split(",")).filter(gate -> !gate.isBlank()).toList(); }
  public List<VerificationPolicySpec> getVerificationPolicies() { return verificationPolicies.stream().map(RepositoryVerificationPolicy::toSpec).toList(); }
  public AgentLoopBudget getAgentLoopBudget() { return agentLoopBudget == null ? null : agentLoopBudget.copy(); }
  public String getTestFirstGate() { return testFirstGate; }
  public List<String> getTestPathGlobs() { return testPathGlobs == null || testPathGlobs.isBlank() ? List.of() : testPathGlobs.lines().toList(); }
}
