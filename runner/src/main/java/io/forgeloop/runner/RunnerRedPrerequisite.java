package io.forgeloop.runner;

/** Optional server-dispatched RED evidence identity used by test-first loop preflight. */
public record RunnerRedPrerequisite(String testTaskId, String targetSha, String evidenceDigest) { }
