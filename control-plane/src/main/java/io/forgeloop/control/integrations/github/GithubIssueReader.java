package io.forgeloop.control.integrations.github;

import com.fasterxml.jackson.databind.JsonNode;

/** Read-only GitHub issue access; onboarding never submits or replays work. */
public interface GithubIssueReader {
    JsonNode readIssue(long installationId, String repository, int issueNumber);
}
