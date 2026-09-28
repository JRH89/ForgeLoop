package io.forgeloop.control.api;

import io.forgeloop.control.application.RepositoryConnectionService;
import io.forgeloop.control.application.RunDeletionService;
import io.forgeloop.control.application.RunArchiveService;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.RepositoryConnection;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

@Controller
public class OperatorWorkflowController {
    private final RunArchiveService archives;
    private final RunDeletionService deletions;
    private final RepositoryConnectionService connections;
    public OperatorWorkflowController(RunArchiveService archives, RunDeletionService deletions, RepositoryConnectionService connections) {
        this.archives = archives; this.deletions = deletions; this.connections = connections;
    }
    @MutationMapping public FeatureRun archiveFeatureRun(@Argument String runId, @Argument boolean archived) {
        return archives.archive(runId, archived);
    }
    @MutationMapping public boolean deleteFeatureRun(@Argument String runId) {
        return deletions.delete(runId);
    }
    @MutationMapping public RepositoryConnection configureRepositoryIntake(@Argument String repository, @Argument boolean requireAssignee, @Argument String requiredAssignee) {
        return connections.configureIntake(repository, requireAssignee, requiredAssignee);
    }
}
