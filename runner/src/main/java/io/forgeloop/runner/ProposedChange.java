package io.forgeloop.runner;

/** One complete-file change emitted by an agent; paths are validated before any repository write. */
public record ProposedChange(String path, String content, String message) {
    public ProposedChange {
        if (path == null || !path.matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,499}") || path.contains("..") || path.startsWith("/")
                || content == null || content.length() > 500_000 || message == null || message.isBlank()) {
            throw new IllegalArgumentException("Proposed change is unsafe or incomplete");
        }
    }
}
