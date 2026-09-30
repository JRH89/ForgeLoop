package io.forgeloop.control.integrations.github;

import java.util.List;

/** Narrow GitHub boundary: orchestration never depends directly on HTTP or token details. */
public interface GithubApi {
    List<GithubInstalledRepository> listInstallationRepositories(long installationId);
    /** Issues GitHub's short-lived installation token for one authenticated runner push. */
    String issueInstallationToken(long installationId);
    String getBranchHead(long installationId, String repository, String branch);
    /** Returns GitHub's changed-file identities; an adapter without this safety check must fail closed. */
    default List<GithubChangedFile> compareFiles(long installationId, String repository, String base, String head) {
        throw new UnsupportedOperationException("GitHub compare-files is not supported by this adapter");
    }
    long createCompletedCheck(long installationId, String repository, String headSha, String name, String summary);
    long createPullRequest(long installationId, String repository, String head, String base, String title, String body, boolean draft);
    /** Creates one issue without labels or assignees; issue intake remains an explicit policy decision. */
    GithubIssueReceipt createIssue(long installationId, String repository, String title, String body);
    /** Closes a source issue after its verified pull request has merged. */
    void closeIssue(long installationId, String repository, int issueNumber);
    boolean checksPass(long installationId, String repository, String headSha);
    String getPullRequestHead(long installationId, String repository, long pullRequestNumber);
    String getPullRequestState(long installationId, String repository, long pullRequestNumber);
    String mergePullRequest(long installationId, String repository, long pullRequestNumber, String expectedHeadSha);
}
