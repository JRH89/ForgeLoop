package io.forgeloop.runner;

/** Committed task output and its metadata-only provider usage. */
public record GuardedPatchResult(String commitSha, ProviderUsageEvidence usage) { }
