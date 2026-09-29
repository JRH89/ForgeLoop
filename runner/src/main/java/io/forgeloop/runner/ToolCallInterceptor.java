package io.forgeloop.runner;

import java.io.IOException;

/** Policy hook at the one tool execution boundary. */
public interface ToolCallInterceptor {
    String name();
    Decision before(ToolCall call, ToolContext context) throws IOException, InterruptedException;
    ToolOutcome after(ToolCall call, ToolOutcome outcome, ToolContext context) throws IOException, InterruptedException;

    record Decision(Kind kind, FailureCategory category, String message, String toolName) {
        public enum Kind { ALLOW, DENY, REDIRECT }
        public Decision {
            if (kind == null || (kind == Kind.ALLOW && (category != null || message != null || toolName != null))
                    || (kind != Kind.ALLOW && (category == null || message == null || message.isBlank()))
                    || (kind == Kind.REDIRECT && (toolName == null || !toolName.matches("[a-z][a-z0-9_]{0,63}"))))
                throw new IllegalArgumentException("Tool interceptor decision is invalid");
        }
        public static Decision allow() { return new Decision(Kind.ALLOW, null, null, null); }
        public static Decision deny(FailureCategory category, String message) { return new Decision(Kind.DENY, category, message, null); }
        public static Decision redirect(FailureCategory category, String toolName, String message) { return new Decision(Kind.REDIRECT, category, message, toolName); }
    }
}
