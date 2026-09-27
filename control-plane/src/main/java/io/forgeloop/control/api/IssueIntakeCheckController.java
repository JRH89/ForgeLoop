package io.forgeloop.control.api;

import io.forgeloop.control.application.RepositoryConnectionService;
import io.forgeloop.control.integrations.github.GithubIssueIntakePolicy;
import io.forgeloop.control.integrations.github.GithubIssueReader;
import java.time.Instant;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/** Tenant-scoped diagnostic. Eligibility is not a promise of webhook delivery or execution. */
@Controller
public class IssueIntakeCheckController {
    private final RepositoryConnectionService repositories;
    private final GithubIssueReader issues;

    public IssueIntakeCheckController(RepositoryConnectionService repositories, GithubIssueReader issues) {
        this.repositories = repositories;
        this.issues = issues;
    }

    @QueryMapping
    public IssueIntakeCheck issueIntakeCheck(@Argument String repository, @Argument int issueNumber) {
        if (issueNumber < 1) throw new IllegalArgumentException("Enter a positive issue number.");
        // Check ownership before making any external request, including requests for private repositories.
        var connection = repositories.requireEnabled(repository);
        List<String> reasons;
        try {
            var issue = issues.readIssue(connection.getInstallationId(), repository, issueNumber);
            if (issue == null || issue.path("number").asInt() != issueNumber
                    || !List.of("open", "closed").contains(issue.path("state").asText()))
                throw new IllegalStateException("Invalid issue response");
            reasons = GithubIssueIntakePolicy.reasons(connection, issue);
        } catch (RuntimeException exception) {
            // Never return GitHub response bodies, tokens, or private issue content to the caller.
            throw new IllegalArgumentException("Unable to read this issue. Check the issue number and GitHub App repository access.");
        }
        return new IssueIntakeCheck(reasons.isEmpty(), reasons, Instant.now().toString());
    }

    public record IssueIntakeCheck(boolean eligible, List<String> reasons, String checkedAt) { }
}
