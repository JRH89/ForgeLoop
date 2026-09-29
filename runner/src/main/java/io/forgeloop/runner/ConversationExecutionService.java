package io.forgeloop.runner;

/** Executes one conversation turn with the same bounded retry policy used by single-call providers. */
public final class ConversationExecutionService {
    public ConversationExecution converse(ConversationClient client, ConversationRequest request, int maxAttempts)
            throws ProviderExecutionFailure {
        if (client == null || request == null || maxAttempts < 1 || maxAttempts > 3)
            throw new IllegalArgumentException("Conversation execution inputs are invalid");
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return new ConversationExecution(client.converse(request), attempt);
            } catch (ProviderException failure) {
                if (!failure.retryable() || attempt == maxAttempts) throw new ProviderExecutionFailure(failure, attempt);
            }
        }
        throw new IllegalStateException("Conversation attempt loop exhausted unexpectedly");
    }
}
