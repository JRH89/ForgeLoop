package io.forgeloop.control.integrations.github;

/** Minimal repository metadata returned by GitHub for one App installation. */
public record GithubInstalledRepository(String fullName, String defaultBranch) { }
