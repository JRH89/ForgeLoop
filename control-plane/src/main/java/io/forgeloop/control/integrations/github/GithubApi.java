package io.forgeloop.control.integrations.github;

import java.util.List;

/** Narrow GitHub boundary: orchestration never depends directly on HTTP or token details. */
public interface GithubApi {
    List<GithubInstalledRepository> listInstallationRepositories(long installationId);
    /** Issues GitHub's short-lived installation token for one authenticated runner push. */
    String issueInstallationToken(long installationId);
    String getBranchHead(long installationId, String repository, String branch);
    long createCompletedCheck(long installationId, String repository, String headSha, String name, String summary);
    long createDraftPullRequest(long installationId, String repository, String head, String base, String title, String body);
}
