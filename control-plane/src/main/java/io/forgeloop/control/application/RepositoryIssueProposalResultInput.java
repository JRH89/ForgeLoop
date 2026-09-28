package io.forgeloop.control.application;

import java.util.List;

/** Runner report for proposal generation. Null provider metadata means the provider returned no billable usage. */
public record RepositoryIssueProposalResultInput(boolean passed, String title, String body,
                                                List<String> acceptanceCriteria, String provider, String model,
                                                long inputTokens, long outputTokens, long estimatedCostMicros,
                                                boolean costKnown) { }
