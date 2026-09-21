package io.forgeloop.runner;

/** Provider returned successfully, but its output could not safely become a committed patch. */
public final class GuardedPatchFailure extends Exception {
    private final ProviderUsageEvidence usage;
    private final String category;
    public GuardedPatchFailure(String category, ProviderUsageEvidence usage, Throwable cause) {
        super(category, cause); this.category = category; this.usage = usage;
    }
    public ProviderUsageEvidence usage() { return usage; }
    public String category() { return category; }
}
