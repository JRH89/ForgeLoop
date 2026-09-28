package io.forgeloop.control.application;

import java.util.List;

public record IssueChatTurnResultInput(boolean passed, String assistantMessage, String title, String body,
                                       List<String> acceptanceCriteria, String provider, String model,
                                       long inputTokens, long outputTokens, long estimatedCostMicros,
                                       boolean costKnown) { }
