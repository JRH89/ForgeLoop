package io.forgeloop.runner;

/** A provider response that consumed usage but failed the strict planner schema. */
public final class PlannerOutputFailure extends RuntimeException {
    private final ProviderUsageEvidence usage;

    public PlannerOutputFailure(ProviderUsageEvidence usage, Throwable cause) {
        super("Provider planner output was rejected", cause);
        this.usage = usage;
    }

    public ProviderUsageEvidence usage() { return usage; }
}
