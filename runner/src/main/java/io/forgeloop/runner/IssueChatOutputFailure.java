package io.forgeloop.runner;

/** Preserves billable provider usage when the model returns a response that cannot be safely used. */
public final class IssueChatOutputFailure extends RuntimeException {
    private final ProviderUsageEvidence usage;

    public IssueChatOutputFailure(ProviderUsageEvidence usage, Throwable cause) {
        super("Provider issue-chat output was invalid", cause);
        this.usage = usage;
    }

    public ProviderUsageEvidence usage() { return usage; }
}
