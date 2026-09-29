package io.forgeloop.runner;

/** Non-model-visible tool-path failure that should terminate the loop as a harness failure. */
public final class LoopHarnessFailure extends Exception {
    private final String category;
    public LoopHarnessFailure(String category, String message, Throwable cause) {
        super(message, cause);
        if (category == null || !category.matches("[A-Z_]{1,80}")) throw new IllegalArgumentException("Harness failure category is invalid");
        this.category = category;
    }
    public String category() { return category; }
}
