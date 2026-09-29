package io.forgeloop.runner;

/** Successful conversation response together with its bounded retry count. */
public record ConversationExecution(ConversationTurn turn, int attemptCount) {
    public ConversationExecution {
        if (turn == null || attemptCount < 1 || attemptCount > 3)
            throw new IllegalArgumentException("Conversation execution is invalid");
    }
}
