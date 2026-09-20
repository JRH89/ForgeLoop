package io.forgeloop.control.api;

import io.forgeloop.control.application.*;
import io.forgeloop.control.domain.*;
import io.forgeloop.control.security.OperatorContext;
import io.forgeloop.control.integrations.github.GithubDeliveryService;
import io.forgeloop.control.integrations.github.GithubChange;
import io.forgeloop.control.integrations.github.GithubInstallationRepositorySyncService;
import java.util.List;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;

/** Operator-facing control-plane GraphQL operations. */
@Controller
public class ControlPlaneController {
  private final FeatureRunService runs; private final RepositoryConnectionService connections; private final RunnerService runners; private final OrganizationService organizations; private final OperatorContext operators; private final AuditLedgerService audit; private final GithubDeliveryService githubDelivery; private final GithubInstallationRepositorySyncService installationSync; private final GithubInstallationRepository githubInstallations;
  public ControlPlaneController(FeatureRunService runs, RepositoryConnectionService connections, RunnerService runners, OrganizationService organizations, OperatorContext operators, AuditLedgerService audit, GithubDeliveryService githubDelivery, GithubInstallationRepositorySyncService installationSync, GithubInstallationRepository githubInstallations) { this.runs = runs; this.connections = connections; this.runners = runners; this.organizations = organizations; this.operators = operators; this.audit = audit; this.githubDelivery = githubDelivery; this.installationSync = installationSync; this.githubInstallations = githubInstallations; }
  @QueryMapping public FeatureRun featureRun(@Argument String id) { return runs.get(id); }
  @QueryMapping public List<FeatureRun> featureRuns() { return runs.list(); }
  @QueryMapping public List<RepositoryConnection> repositoryConnections() { return connections.list(); }
  @QueryMapping public List<Runner> runners(@Argument String organizationId) { operators.requireOrganization(organizationId); return runners.list(organizationId); }
  @QueryMapping public List<OrganizationMembership> organizationMemberships(@Argument String organizationId) { return organizations.memberships(organizationId); }
  /** The run lookup establishes tenant access before its audit history is returned. */
  @QueryMapping public List<AuditLedgerEntry> featureRunAuditEvents(@Argument String runId) { runs.get(runId); return audit.events("FEATURE_RUN", runId); }
  @MutationMapping public FeatureRun submitFeature(@Argument SubmitFeatureInput input) { return runs.submit(new FeatureSubmission(input.repository(), input.sourceRef(), input.title(), input.specification(), input.budgetUsd())); }
  @MutationMapping public RepositoryConnection connectRepository(@Argument ConnectRepositoryInput input) { operators.requireAdministrator(); return connections.register(new RepositoryRegistration(input.repository(), input.installationId(), input.defaultBranch(), input.issueLabel(), input.harnessProfile(), input.requiredGates(), input.maxBudgetUsd())); }
  @MutationMapping public String issueRunnerRegistrationToken(@Argument String organizationId) { operators.requireAdministrator(); operators.requireOrganization(organizationId); return runners.issueRegistrationToken(organizationId); }
  @MutationMapping public RunnerEnrollment registerRunner(@Argument RegisterRunnerInput input) { return runners.register(new RunnerRegistration(input.token(), input.name(), input.version(), input.capabilities())); }
  @MutationMapping public Runner runnerHeartbeat(@Argument String runnerId, @Argument String credential) { return runners.heartbeat(runnerId, credential); }
  @MutationMapping public OrganizationMembership grantOrganizationMembership(@Argument String organizationId, @Argument String subject, @Argument OperatorRole role) { return organizations.grantMembership(organizationId, subject, role); }
  @MutationMapping public DeliveryTask transitionTask(@Argument String taskId, @Argument TaskState to) { return runs.transitionTask(taskId, to); }
  @MutationMapping public FeatureRun recordVerificationGate(@Argument String runId, @Argument String gate, @Argument boolean passed) { return runs.recordGate(runId, gate, passed); }
  @MutationMapping public FeatureRun cancelFeatureRun(@Argument String runId) { operators.requireAdministrator(); return runs.cancel(runId); }
  @MutationMapping public List<RepositoryConnection> synchronizeGithubInstallation(@Argument long installationId) { operators.requireAdministrator(); operators.requireOrganization(githubInstallations.findByInstallationId(installationId).orElseThrow(() -> new IllegalArgumentException("GitHub installation is not registered")).getOrganizationId()); installationSync.synchronizeInstallation(installationId); return connections.list(); }
  /** Temporary controlled handoff until Slice 3 supplies runner-produced changes and base commits. */
  @MutationMapping public GithubPublication deliverVerifiedRun(@Argument String runId, @Argument String baseSha, @Argument List<GithubChangeInput> changes, @Argument String summary) { operators.requireAdministrator(); FeatureRun run = runs.get(runId); RepositoryConnection connection = connections.requireEnabled(run.getRepository()); return githubDelivery.deliver(run, connection.getInstallationId(), baseSha, changes.stream().map(change -> new GithubChange(change.path(), change.content(), change.message())).toList(), summary); }
  public record SubmitFeatureInput(String repository, String sourceRef, String title, String specification, double budgetUsd) { }
  public record ConnectRepositoryInput(String repository, long installationId, String defaultBranch, String issueLabel, String harnessProfile, List<String> requiredGates, double maxBudgetUsd) { }
  public record RegisterRunnerInput(String token, String name, String version, List<String> capabilities) { }
  public record GithubChangeInput(String path, String content, String message) { }
}
