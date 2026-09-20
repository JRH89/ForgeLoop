package io.forgeloop.control.api;

import io.forgeloop.control.application.*;
import io.forgeloop.control.domain.*;
import io.forgeloop.control.security.OperatorContext;
import java.util.List;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;

/** Operator-facing control-plane GraphQL operations. */
@Controller
public class ControlPlaneController {
  private final FeatureRunService runs; private final RepositoryConnectionService connections; private final RunnerService runners; private final OrganizationService organizations; private final OperatorContext operators;
  public ControlPlaneController(FeatureRunService runs, RepositoryConnectionService connections, RunnerService runners, OrganizationService organizations, OperatorContext operators) { this.runs = runs; this.connections = connections; this.runners = runners; this.organizations = organizations; this.operators = operators; }
  @QueryMapping public FeatureRun featureRun(@Argument String id) { return runs.get(id); }
  @QueryMapping public List<FeatureRun> featureRuns() { return runs.list(); }
  @QueryMapping public List<RepositoryConnection> repositoryConnections() { return connections.list(); }
  @QueryMapping public List<Runner> runners(@Argument String organizationId) { operators.requireOrganization(organizationId); return runners.list(organizationId); }
  @QueryMapping public List<OrganizationMembership> organizationMemberships(@Argument String organizationId) { return organizations.memberships(organizationId); }
  @MutationMapping public FeatureRun submitFeature(@Argument SubmitFeatureInput input) { return runs.submit(new FeatureSubmission(input.repository(), input.sourceRef(), input.title(), input.specification(), input.budgetUsd())); }
  @MutationMapping public RepositoryConnection connectRepository(@Argument ConnectRepositoryInput input) { operators.requireAdministrator(); return connections.register(new RepositoryRegistration(input.repository(), input.installationId(), input.defaultBranch(), input.issueLabel(), input.harnessProfile(), input.requiredGates(), input.maxBudgetUsd())); }
  @MutationMapping public String issueRunnerRegistrationToken(@Argument String organizationId) { operators.requireAdministrator(); operators.requireOrganization(organizationId); return runners.issueRegistrationToken(organizationId); }
  @MutationMapping public RunnerEnrollment registerRunner(@Argument RegisterRunnerInput input) { return runners.register(new RunnerRegistration(input.token(), input.name(), input.version(), input.capabilities())); }
  @MutationMapping public Runner runnerHeartbeat(@Argument String runnerId, @Argument String credential) { return runners.heartbeat(runnerId, credential); }
  @MutationMapping public OrganizationMembership grantOrganizationMembership(@Argument String organizationId, @Argument String subject, @Argument OperatorRole role) { return organizations.grantMembership(organizationId, subject, role); }
  @MutationMapping public DeliveryTask transitionTask(@Argument String taskId, @Argument TaskState to) { return runs.transitionTask(taskId, to); }
  @MutationMapping public FeatureRun recordVerificationGate(@Argument String runId, @Argument String gate, @Argument boolean passed) { return runs.recordGate(runId, gate, passed); }
  @MutationMapping public FeatureRun cancelFeatureRun(@Argument String runId) { operators.requireAdministrator(); return runs.cancel(runId); }
  public record SubmitFeatureInput(String repository, String sourceRef, String title, String specification, double budgetUsd) { }
  public record ConnectRepositoryInput(String repository, long installationId, String defaultBranch, String issueLabel, String harnessProfile, List<String> requiredGates, double maxBudgetUsd) { }
  public record RegisterRunnerInput(String token, String name, String version, List<String> capabilities) { }
}
