package io.forgeloop.control.api;

import io.forgeloop.control.application.RepositoryConnectionService;
import io.forgeloop.control.application.RunArchiveService;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.RepositoryConnection;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

@Controller
public class OperatorWorkflowController {
    private final RunArchiveService archives;
    private final RepositoryConnectionService connections;
    public OperatorWorkflowController(RunArchiveService archives, RepositoryConnectionService connections) {
        this.archives = archives; this.connections = connections;
    }
    @MutationMapping public FeatureRun archiveFeatureRun(@Argument String runId, @Argument boolean archived) {
        return archives.archive(runId, archived);
    }
    @MutationMapping public RepositoryConnection configureRepositoryIntake(@Argument String repository, @Argument String requiredAssignee) {
        return connections.configureIntake(repository, requiredAssignee);
    }
}
