package io.forgeloop.runner;

import java.util.List;

/** Only redacted scan finding evidence accompanies an explicitly requested proposal job. */
public record RepositoryIssueProposalGrant(String id, String repository, String commitSha, String severity,
                                           String findingTitle, String description, String impact, String evidence,
                                           List<String> affectedFiles, List<String> acceptanceCriteria) { }
