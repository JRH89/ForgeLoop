package io.forgeloop.control.api;

import io.forgeloop.control.application.*;
import io.forgeloop.control.domain.*;
import io.forgeloop.control.security.OperatorContext;
import io.forgeloop.control.integrations.github.GithubDeliveryService;
import io.forgeloop.control.integrations.github.GithubInstallationRepositorySyncService;
import java.util.List;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;

/** Operator-facing control-plane GraphQL operations. */
@Controller
public class ControlPlaneController {
  private final FeatureRunService runs; private final RepositoryConnectionService connections; private final RunnerService runners; private final OrganizationService organizations; private final OperatorContext operators; private final AuditLedgerService audit; private final GithubDeliveryService githubDelivery; private final GithubInstallationRepositorySyncService installationSync; private final GithubInstallationRepository githubInstallations; private final VerificationEvidenceRepository evidence; private final ReviewEvidenceRepository reviews; private final GithubPublicationRepository publications; private final ArtifactMetadataRepository artifacts; private final RunnerEventRepository events; private final HumanEscalationService escalations;
  public ControlPlaneController(FeatureRunService runs, RepositoryConnectionService connections, RunnerService runners, OrganizationService organizations, OperatorContext operators, AuditLedgerService audit, GithubDeliveryService githubDelivery, GithubInstallationRepositorySyncService installationSync, GithubInstallationRepository githubInstallations, VerificationEvidenceRepository evidence, ReviewEvidenceRepository reviews, GithubPublicationRepository publications, ArtifactMetadataRepository artifacts, RunnerEventRepository events, HumanEscalationService escalations) { this.runs = runs; this.connections = connections; this.runners = runners; this.organizations = organizations; this.operators = operators; this.audit = audit; this.githubDelivery = githubDelivery; this.installationSync = installationSync; this.githubInstallations = githubInstallations; this.evidence = evidence; this.reviews = reviews; this.publications = publications; this.artifacts = artifacts; this.events=events;this.escalations=escalations; }
  @QueryMapping public FeatureRun featureRun(@Argument String id) { return runs.get(id); }
  @QueryMapping public List<FeatureRun> featureRuns() { return runs.list(); }
  @QueryMapping public List<RepositoryConnection> repositoryConnections() { return connections.list(); }
  @QueryMapping public List<Runner> runners(@Argument String organizationId) { operators.requireOrganization(organizationId); return runners.list(organizationId); }
  @QueryMapping public List<OrganizationMembership> organizationMemberships(@Argument String organizationId) { return organizations.memberships(organizationId); }
  /** The run lookup establishes tenant access before its audit history is returned. */
  @QueryMapping public List<AuditLedgerEntry> featureRunAuditEvents(@Argument String runId) { runs.get(runId); return audit.events("FEATURE_RUN", runId); }
  @QueryMapping public List<VerificationEvidence> featureRunEvidence(@Argument String runId) { runs.get(runId); return evidence.findByTask_Run_IdOrderByRecordedAtAsc(runId); }
  @QueryMapping public List<ReviewEvidence> featureRunReviewEvidence(@Argument String runId) { runs.get(runId); return reviews.findByTask_Run_IdOrderByRecordedAtAsc(runId); }
  @QueryMapping public List<ArtifactMetadata> featureRunArtifacts(@Argument String runId) { runs.get(runId); return artifacts.findByRunIdOrderByCreatedAtAsc(runId); }
  @QueryMapping public List<RunnerEvent> featureRunEvents(@Argument String runId) { runs.get(runId); return events.findByRunIdOrderByOccurredAtAscSequenceNumberAsc(runId); }
  @QueryMapping public List<HumanEscalation> featureRunEscalations(@Argument String runId) { runs.get(runId); return escalations.list(runId); }
  @QueryMapping public GithubPublication featureRunPublication(@Argument String runId) { runs.get(runId); return publications.findByFeatureRunId(runId).orElse(null); }
  @QueryMapping public OperatorSession currentOperator() { return new OperatorSession(operators.subject(), operators.organizationId(), operators.role()); }
  @MutationMapping public FeatureRun submitFeature(@Argument SubmitFeatureInput input) { return runs.submit(new FeatureSubmission(input.repository(), input.sourceRef(), input.title(), input.specification(), input.budgetUsd())); }
  @MutationMapping public RepositoryConnection connectRepository(@Argument ConnectRepositoryInput input) { operators.requireAdministrator(); return connections.register(new RepositoryRegistration(input.repository(), input.installationId(), input.defaultBranch(), input.issueLabel(), input.harnessProfile(), input.requiredGates(), input.maxBudgetUsd())); }
  @MutationMapping public RepositoryConnection configureRepositoryVerification(@Argument String repository, @Argument List<VerificationPolicyInput> policies) { operators.requireAdministrator(); return connections.configureVerification(repository, policies.stream().map(VerificationPolicyInput::toSpec).toList()); }
  @MutationMapping public String issueRunnerRegistrationToken(@Argument String organizationId) { operators.requireAdministrator(); operators.requireOrganization(organizationId); return runners.issueRegistrationToken(organizationId); }
  @MutationMapping public RunnerEnrollment registerRunner(@Argument RegisterRunnerInput input) { return runners.register(new RunnerRegistration(input.token(), input.name(), input.version(), input.capabilities())); }
  @MutationMapping public Runner runnerHeartbeat(@Argument String runnerId, @Argument String credential) { return runners.heartbeat(runnerId, credential); }
  @MutationMapping public OrganizationMembership grantOrganizationMembership(@Argument String organizationId, @Argument String subject, @Argument OperatorRole role) { return organizations.grantMembership(organizationId, subject, role); }
  @MutationMapping public FeatureRun overrideVerificationGate(@Argument String runId, @Argument String gate, @Argument String reason) { operators.requireAdministrator(); return runs.overrideGate(runId, gate, reason); }
  @MutationMapping public FeatureRun cancelFeatureRun(@Argument String runId, @Argument String confirmation) { operators.requireOperator(); if(!"CANCEL".equals(confirmation))throw new IllegalArgumentException("Cancellation confirmation must equal CANCEL"); return runs.cancel(runId); }
  @MutationMapping public DeliveryTask retryFeatureTask(@Argument String taskId, @Argument String reason, @Argument String confirmation) { operators.requireOperator(); if(!"RETRY".equals(confirmation))throw new IllegalArgumentException("Retry confirmation must equal RETRY"); return runs.retry(taskId, reason); }
  @MutationMapping public FeatureRun approveFeatureRun(@Argument String runId, @Argument String confirmation) { operators.requireAdministrator(); FeatureRun run=runs.approve(runId, operators.subject(), confirmation); GithubPublication publication=publications.findByFeatureRunId(runId).orElse(null); if(publication!=null&&publication.getHeadSha()!=null){RepositoryConnection connection=connections.requireEnabled(run.getRepository());githubDelivery.deliverPushed(run,connection.getInstallationId(),"All required ForgeLoop verification gates passed with checksummed evidence.");} return run; }
  @MutationMapping public HumanEscalation acknowledgeEscalation(@Argument String id,@Argument String confirmation){operators.requireOperator();if(!"ACKNOWLEDGE".equals(confirmation))throw new IllegalArgumentException("Escalation confirmation must equal ACKNOWLEDGE");HumanEscalation item=escalations.acknowledge(id,operators.organizationId(),operators.subject());audit.record("ESCALATION_ACKNOWLEDGED","FEATURE_RUN",item.getRunId(),id);return item;}
  @MutationMapping public HumanEscalation resolveEscalation(@Argument String id,@Argument String confirmation){operators.requireOperator();if(!"RESOLVE".equals(confirmation))throw new IllegalArgumentException("Escalation confirmation must equal RESOLVE");HumanEscalation item=escalations.resolve(id,operators.organizationId(),operators.subject());audit.record("ESCALATION_RESOLVED","FEATURE_RUN",item.getRunId(),id);return item;}
  @MutationMapping public List<RepositoryConnection> synchronizeGithubInstallation(@Argument long installationId) { operators.requireAdministrator(); operators.requireOrganization(githubInstallations.findByInstallationId(installationId).orElseThrow(() -> new IllegalArgumentException("GitHub installation is not registered")).getOrganizationId()); installationSync.synchronizeInstallation(installationId); return connections.list(); }
  public record SubmitFeatureInput(String repository, String sourceRef, String title, String specification, double budgetUsd) { }
  public record ConnectRepositoryInput(String repository, long installationId, String defaultBranch, String issueLabel, String harnessProfile, List<String> requiredGates, double maxBudgetUsd) { }
  public record RegisterRunnerInput(String token, String name, String version, List<String> capabilities) { }
  public record OperatorSession(String subject, String organizationId, OperatorRole role) { }
  public record VerificationPolicyInput(String name, String kind, String imageDigest, List<String> command, String networkPolicy, int timeoutSeconds, boolean required, String criterionCoverage) { VerificationPolicySpec toSpec(){return new VerificationPolicySpec(name,kind,imageDigest,command,networkPolicy,timeoutSeconds,required,criterionCoverage);} }
}
