package io.forgeloop.runner;

public record IssueChatResult(String assistantMessage, RepositoryIssueSpecification specification,
                              ProviderUsageEvidence usage) { }
