package io.forgeloop.control.integrations.github;

import com.fasterxml.jackson.databind.JsonNode;
import io.forgeloop.control.domain.RepositoryConnection;
import java.util.ArrayList;
import java.util.List;

/** One eligibility policy for webhook intake and the read-only setup diagnostic. */
public final class GithubIssueIntakePolicy {
    private GithubIssueIntakePolicy() { }

    public static List<String> reasons(RepositoryConnection connection, JsonNode issue) {
        List<String> reasons = new ArrayList<>();
        if (!connection.isEnabled()) reasons.add("Repository connection is disabled.");
        if (issue.has("pull_request")) reasons.add("Choose an issue, not a pull request.");
        if ("closed".equals(issue.path("state").asText())) reasons.add("The issue is closed.");
        if (issue.path("labels").findValuesAsText("name").stream().noneMatch(connection::acceptsIssueLabel))
            reasons.add("Add the required label: " + connection.getIssueLabel());
        if (!connection.acceptsAssignees(issue.path("assignees").findValuesAsText("login")))
            reasons.add(connection.getRequiredAssignee() == null
                    ? "Assign the issue to a GitHub user before intake."
                    : "Assign the issue to: " + connection.getRequiredAssignee());
        if (issue.path("body").asText("").isBlank()) reasons.add("Add a description and acceptance criteria to the issue body.");
        return List.copyOf(reasons);
    }
}
