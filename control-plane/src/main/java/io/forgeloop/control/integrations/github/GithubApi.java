package io.forgeloop.control.integrations.github;

import java.util.List;

/** Narrow GitHub boundary: orchestration never depends directly on HTTP or token details. */
public interface GithubApi {
    List<GithubInstalledRepository> listInstallationRepositories(long installationId);
    void createBranch(long installationId, String repository, String branch, String baseSha);
    String putFile(long installationId, String repository, String branch, GithubChange change);
    long createCompletedCheck(long installationId, String repository, String headSha, String name, String summary);
    long createDraftPullRequest(long installationId, String repository, String head, String base, String title, String body);
}
