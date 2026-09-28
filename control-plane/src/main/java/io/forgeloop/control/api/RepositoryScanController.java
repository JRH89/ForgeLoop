package io.forgeloop.control.api;

import io.forgeloop.control.application.RepositoryScanService;
import io.forgeloop.control.domain.RepositoryScan;
import io.forgeloop.control.domain.RepositoryScanFinding;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/** Browser operations for explicit repository scans and reviewed issue proposals. */
@Controller
public class RepositoryScanController {
    private final RepositoryScanService scans;
    public RepositoryScanController(RepositoryScanService scans) { this.scans = scans; }
    @QueryMapping public List<RepositoryScan> repositoryScans(@Argument String repository) { return scans.list(repository); }
    @MutationMapping public RepositoryScan requestRepositoryScan(@Argument String repository) { return scans.request(repository); }
    @MutationMapping public RepositoryScanFinding createRepositoryScanIssue(@Argument String scanId, @Argument String findingId) {
        return scans.createIssue(scanId, findingId);
    }
}
