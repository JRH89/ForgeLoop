package io.forgeloop.control.application;

/** Short-lived checkout grant for exactly one approved repository scan. */
public record RepositoryScanGrant(String id, String repository, String baseBranch, String token) { }
