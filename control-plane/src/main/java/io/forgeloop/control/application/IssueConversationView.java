package io.forgeloop.control.application;

import io.forgeloop.control.domain.IssueConversation;
import java.util.List;

public record IssueConversationView(String id, String repository, String status, String createdAt, String updatedAt,
                                    String failureSummary, String draftTitle, String draftBody,
                                    List<String> acceptanceCriteria, String provider, String model,
                                    long inputTokens, long outputTokens, long estimatedCostMicros,
                                    boolean costKnown, Integer issueNumber, String issueUrl,
                                    List<IssueChatMessageView> messages) {
    public static IssueConversationView from(IssueConversation conversation, List<IssueChatMessageView> messages) {
        return new IssueConversationView(conversation.getId(), conversation.getRepository(), conversation.getStatus(),
                conversation.getCreatedAt(), conversation.getUpdatedAt(), conversation.getFailureSummary(),
                conversation.getDraftTitle(), conversation.getDraftBody(), conversation.getAcceptanceCriteria(),
                conversation.getProvider(), conversation.getModel(), conversation.getInputTokens(),
                conversation.getOutputTokens(), conversation.getEstimatedCostMicros(), conversation.isCostKnown(),
                conversation.getIssueNumber(), conversation.getIssueUrl(), messages);
    }
}
