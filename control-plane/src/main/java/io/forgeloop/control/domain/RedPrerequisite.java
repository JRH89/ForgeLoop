package io.forgeloop.control.domain;

/** Current passing RED proof sent with a test-first implementation task. */
public record RedPrerequisite(String testTaskId, String targetSha, String evidenceDigest) { }
