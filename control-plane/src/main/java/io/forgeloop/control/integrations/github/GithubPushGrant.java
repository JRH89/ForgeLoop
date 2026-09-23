package io.forgeloop.control.integrations.github;

/** Short-lived credential returned only to the runner that owns the active integration lease. */
public record GithubPushGrant(String repository, String branch, String expectedHeadSha, String token) { }
