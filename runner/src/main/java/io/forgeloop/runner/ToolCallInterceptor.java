package io.forgeloop.runner;

import java.io.IOException;

/** Policy hook at the one tool execution boundary. */
public interface ToolCallInterceptor {
    String name();
    Decision before(ToolCall call, ToolContext context) throws IOException, InterruptedException;
    ToolOutcome after(ToolCall call, ToolOutcome outcome, ToolContext context) throws IOException, InterruptedException;

    record Decision(Kind kind, FailureCategory category, String message, String toolName, HoldClass holdClass) {
        public enum Kind { ALLOW, DENY, REDIRECT, HOLD }
        public Decision {
            if (kind == null || (kind == Kind.ALLOW && (category != null || message != null || toolName != null))
                    || (kind != Kind.ALLOW && (category == null || message == null || message.isBlank()))
                    || (kind == Kind.REDIRECT && (toolName == null || !toolName.matches("[a-z][a-z0-9_]{0,63}") || holdClass != null))
                    || (kind == Kind.HOLD && (category != FailureCategory.PERMISSION || toolName != null || holdClass == null))
                    || (kind != Kind.HOLD && holdClass != null))
                throw new IllegalArgumentException("Tool interceptor decision is invalid");
        }
        public Decision(Kind kind, FailureCategory category, String message, String toolName) {
            this(kind, category, message, toolName, null);
        }
        public static Decision allow() { return new Decision(Kind.ALLOW, null, null, null, null); }
        public static Decision deny(FailureCategory category, String message) { return new Decision(Kind.DENY, category, message, null, null); }
        public static Decision redirect(FailureCategory category, String toolName, String message) { return new Decision(Kind.REDIRECT, category, message, toolName, null); }
        public static Decision hold(HoldClass holdClass, String reason) { return new Decision(Kind.HOLD, FailureCategory.PERMISSION, reason, null, holdClass); }
    }
}
