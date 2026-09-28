package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.forgeloop.control.domain.RepositoryConnection;
import java.util.List;
import org.junit.jupiter.api.Test;

class GithubIssueIntakePolicyTest {
    private final RepositoryConnection connection = new RepositoryConnection("org", "acme/project", 12, "main", "forgeloop", "JVM_REACT", List.of("compile"), 20);
    private final ObjectMapper json = new ObjectMapper();

    @Test void reportsEachMissingRequirement() throws Exception {
        connection.configureRequiredAssignee("owner");
        var reasons = GithubIssueIntakePolicy.reasons(connection, json.readTree("""
            {"state":"open","body":null,"labels":[],"assignees":[]}
            """));
        assertEquals(List.of("Add the required label: forgeloop", "Assign the issue to: owner", "Add a description and acceptance criteria to the issue body."), reasons);
    }

    @Test void acceptsCaseInsensitiveAssigneeAndExactLabel() throws Exception {
        connection.configureRequiredAssignee("owner");
        assertTrue(GithubIssueIntakePolicy.reasons(connection, json.readTree("""
            {"state":"open","body":"Do the work","labels":[{"name":"forgeloop"}],"assignees":[{"login":"OWNER"}]}
            """)).isEmpty());
    }

    @Test void requiresAnyAssigneeWhenConfiguredWithoutAnExactLogin() throws Exception {
        connection.configureAssignmentPolicy(true, "");
        var unassigned = GithubIssueIntakePolicy.reasons(connection, json.readTree("""
            {"state":"open","body":"Do the work","labels":[{"name":"forgeloop"}],"assignees":[]}
            """));
        assertTrue(unassigned.contains("Assign the issue to a GitHub user before intake."));
        var assigned = GithubIssueIntakePolicy.reasons(connection, json.readTree("""
            {"state":"open","body":"Do the work","labels":[{"name":"forgeloop"}],"assignees":[{"login":"worker"}]}
            """));
        assertTrue(assigned.isEmpty());
    }

    @Test void rejectsClosedIssuesAndPullRequests() throws Exception {
        var reasons = GithubIssueIntakePolicy.reasons(connection, json.readTree("""
            {"state":"closed","pull_request":{},"body":"Do the work","labels":[{"name":"forgeloop"}]}
            """));
        assertEquals(List.of("Choose an issue, not a pull request.", "The issue is closed."), reasons);
    }

    @Test void blankBodyCannotStartWork() throws Exception {
        assertTrue(GithubIssueIntakePolicy.reasons(connection, json.readTree("""
            {"state":"open","body":"  ","labels":[{"name":"forgeloop"}]}
            """)).stream().anyMatch(reason -> reason.contains("description")));
    }
}
