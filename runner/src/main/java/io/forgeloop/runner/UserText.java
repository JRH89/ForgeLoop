package io.forgeloop.runner;

/** User-provided conversation text; repository content remains untrusted input. */
public record UserText(String text) implements ConversationItem {
    public UserText {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Conversation user text is required");
    }
}
