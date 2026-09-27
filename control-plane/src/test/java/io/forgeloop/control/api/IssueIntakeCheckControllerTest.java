package io.forgeloop.control.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.forgeloop.control.application.RepositoryConnectionService;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.integrations.github.GithubIssueReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class IssueIntakeCheckControllerTest {
    private final RepositoryConnectionService repositories = mock(RepositoryConnectionService.class);
    private final GithubIssueReader issues = mock(GithubIssueReader.class);
    private final IssueIntakeCheckController controller = new IssueIntakeCheckController(repositories, issues);
    private final RepositoryConnection connection = new RepositoryConnection("org", "acme/project", 12, "main", "forgeloop", "JVM_REACT", List.of("compile"), 20);

    @Test void eligibleIssueIsReadOnly() throws Exception {
        when(repositories.requireEnabled("acme/project")).thenReturn(connection);
        when(issues.readIssue(12, "acme/project", 7)).thenReturn(new ObjectMapper().readTree("""
            {"number":7,"state":"open","body":"Acceptance criteria","labels":[{"name":"forgeloop"}],"assignees":[]}
            """));
        var result = controller.issueIntakeCheck("acme/project", 7);
        assertTrue(result.eligible());
        assertTrue(result.reasons().isEmpty());
        assertNotNull(java.time.Instant.parse(result.checkedAt()));
        verify(issues).readIssue(12, "acme/project", 7);
        verifyNoMoreInteractions(issues);
    }

    @Test void authorizationHappensBeforeGithubAccess() {
        when(repositories.requireEnabled("other/private")).thenThrow(new IllegalStateException("Not connected"));
        assertThrows(IllegalStateException.class, () -> controller.issueIntakeCheck("other/private", 1));
        verifyNoInteractions(issues);
    }

    @Test void invalidNumberNeverCallsGithub() {
        assertThrows(IllegalArgumentException.class, () -> controller.issueIntakeCheck("acme/project", 0));
        verifyNoInteractions(repositories, issues);
    }

    @Test void upstreamFailureDoesNotExposePrivateDetails() {
        when(repositories.requireEnabled("acme/project")).thenReturn(connection);
        when(issues.readIssue(12, "acme/project", 7)).thenThrow(new IllegalStateException("private-token-and-body"));
        var exception = assertThrows(IllegalArgumentException.class, () -> controller.issueIntakeCheck("acme/project", 7));
        assertFalse(exception.getMessage().contains("private-token"));
        assertNull(exception.getCause());
    }

    @Test void malformedResponseCannotReportEligibility() throws Exception {
        when(repositories.requireEnabled("acme/project")).thenReturn(connection);
        when(issues.readIssue(12, "acme/project", 7)).thenReturn(new ObjectMapper().readTree("{}"));
        assertThrows(IllegalArgumentException.class, () -> controller.issueIntakeCheck("acme/project", 7));
    }
}
