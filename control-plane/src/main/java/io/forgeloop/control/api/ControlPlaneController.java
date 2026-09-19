package io.forgeloop.control.api;

import io.forgeloop.control.application.FeatureRunService;
import io.forgeloop.control.application.FeatureSubmission;
import io.forgeloop.control.application.RepositoryConnectionService;
import io.forgeloop.control.application.RepositoryRegistration;
import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.TaskState;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class ControlPlaneController {
  private final FeatureRunService runs; private final RepositoryConnectionService connections;
  public ControlPlaneController(FeatureRunService runs, RepositoryConnectionService connections) { this.runs = runs; this.connections = connections; }
  @QueryMapping public FeatureRun featureRun(@Argument String id) { return runs.get(id); }
  @QueryMapping public List<FeatureRun> featureRuns() { return runs.list(); }
  @QueryMapping public List<RepositoryConnection> repositoryConnections() { return connections.list(); }
  @MutationMapping public FeatureRun submitFeature(@Argument SubmitFeatureInput input) { return runs.submit(new FeatureSubmission(input.repository(), input.sourceRef(), input.title(), input.specification(), input.budgetUsd())); }
  @MutationMapping public RepositoryConnection connectRepository(@Argument ConnectRepositoryInput input) { return connections.register(new RepositoryRegistration(input.repository(), input.installationId(), input.defaultBranch(), input.issueLabel(), input.harnessProfile(), input.requiredGates(), input.maxBudgetUsd())); }
  @MutationMapping public DeliveryTask transitionTask(@Argument String taskId, @Argument TaskState to) { return runs.transitionTask(taskId, to); }
  public record SubmitFeatureInput(String repository, String sourceRef, String title, String specification, double budgetUsd) { }
  public record ConnectRepositoryInput(String repository, long installationId, String defaultBranch, String issueLabel, String harnessProfile, List<String> requiredGates, double maxBudgetUsd) { }
}
