package io.forgeloop.runner;

/** Provider-specific conversation transport. Serialized bytes remain runner-local and are not telemetry. */
public interface ConversationClient {
    /** Stable adapter identity recorded with the journal for future replay compatibility checks. */
    default String adapterId() { return getClass().getSimpleName(); }

    /** Increment when this adapter changes its wire serialization contract. */
    default int serializerVersion() { return 1; }

    /** Returns the complete vendor request body used by {@link #converse(ConversationRequest)}. */
    String serialize(ConversationRequest request);

    ConversationTurn converse(ConversationRequest request) throws ProviderException;
}
