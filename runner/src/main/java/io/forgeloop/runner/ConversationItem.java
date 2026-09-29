package io.forgeloop.runner;

/** A single append-only item in a provider conversation. */
public sealed interface ConversationItem permits UserText, AssistantTurn, ToolResults { }
