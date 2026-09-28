package io.forgeloop.runner;

/** Provider returned billable content, but it did not satisfy the issue-specification contract. */
public final class IssueProposalOutputFailure extends Exception {
    private final ProviderUsageEvidence usage;
    public IssueProposalOutputFailure(ProviderUsageEvidence usage, Throwable cause) {
        super("INVALID_ISSUE_PROPOSAL_OUTPUT", cause);
        this.usage = usage;
    }
    public ProviderUsageEvidence usage() { return usage; }
}
