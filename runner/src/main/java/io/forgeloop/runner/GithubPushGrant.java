package io.forgeloop.runner;

/** Short-lived server-authorized destination for exactly one integrated run branch. */
public record GithubPushGrant(String repository, String branch, String expectedHeadSha, String token) {
    public GithubPushGrant {
        if (repository == null || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
                || branch == null || !branch.matches("forgeloop/[A-Za-z0-9_-]{1,80}")
                || (expectedHeadSha != null && !expectedHeadSha.matches("[0-9a-f]{40,64}"))
                || token == null || token.isBlank()) {
            throw new IllegalArgumentException("GitHub push grant is invalid");
        }
    }
}
