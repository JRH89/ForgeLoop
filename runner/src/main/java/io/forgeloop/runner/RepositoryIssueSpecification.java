package io.forgeloop.runner;

import java.util.List;

/** Reviewable issue content. No write or publication capability is attached to this result. */
public record RepositoryIssueSpecification(String title, String body, List<String> acceptanceCriteria) { }
