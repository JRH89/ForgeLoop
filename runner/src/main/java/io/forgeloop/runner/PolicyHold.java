package io.forgeloop.runner;

/** A fail-closed preflight result; reason text is journal metadata, not model input. */
public record PolicyHold(HoldClass holdClass, String check, String reason) {
    public PolicyHold {
        if (holdClass == null || check == null || check.isBlank() || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Policy hold is invalid");
        }
    }
}
