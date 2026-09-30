package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GithubSourceIssueTest {
    @Test
    void parsesOnlyPositiveIntSizedIssueRunReferences() {
        assertEquals(9, GithubSourceIssue.fromSourceRef("issue-9").orElseThrow().number());
        assertTrue(GithubSourceIssue.fromSourceRef("manual-9").isEmpty());
        assertTrue(GithubSourceIssue.fromSourceRef("issue-0").isEmpty());
        assertTrue(GithubSourceIssue.fromSourceRef("issue-2147483648").isEmpty());
    }
}
