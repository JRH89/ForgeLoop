package io.forgeloop.runner;

/** Expected, safe-to-return tool failure. Unexpected exceptions remain harness failures. */
public final class LoopToolFailure extends Exception {
    private final FailureCategory category;
    public LoopToolFailure(FailureCategory category, String message) {
        super(message);
        if (category == null) throw new IllegalArgumentException("Tool failure category is required");
        this.category = category;
    }
    public FailureCategory category() { return category; }
}
