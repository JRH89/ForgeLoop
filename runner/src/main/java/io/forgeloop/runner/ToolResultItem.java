package io.forgeloop.runner;

/** Result for one previously declared and executed tool call. */
public record ToolResultItem(String callId, String toolName, String content, boolean error) {
    public ToolResultItem {
        if (callId == null || callId.isBlank() || toolName == null || !toolName.matches("[a-z][a-z0-9_]{0,63}") || content == null)
            throw new IllegalArgumentException("Tool result is invalid");
    }
}
