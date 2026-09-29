package io.forgeloop.runner;

/** Provider-specific conversation transport. Serialized bytes remain runner-local and are not telemetry. */
public interface ConversationClient {
    /** Returns the complete vendor request body used by {@link #converse(ConversationRequest)}. */
    String serialize(ConversationRequest request);

    ConversationTurn converse(ConversationRequest request) throws ProviderException;
}
