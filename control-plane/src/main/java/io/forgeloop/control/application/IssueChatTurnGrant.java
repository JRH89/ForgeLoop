package io.forgeloop.control.application;

import io.forgeloop.control.domain.IssueConversationMessage;
import java.util.List;

/** Least-privilege runner grant: conversation text only, with no repository checkout or GitHub credential. */
public record IssueChatTurnGrant(String id, String repository, String draftTitle, String draftBody,
                                 List<String> acceptanceCriteria, List<Message> messages) {
    public record Message(String role, String content) { }

    public static IssueChatTurnGrant from(io.forgeloop.control.domain.IssueConversation conversation,
                                           List<IssueConversationMessage> messages) {
        return new IssueChatTurnGrant(conversation.getId(), conversation.getRepository(), conversation.getDraftTitle(),
                conversation.getDraftBody(), conversation.getAcceptanceCriteria(), messages.stream()
                .skip(Math.max(0, messages.size() - 8L)).map(item -> new Message(item.getRole(), item.getContent())).toList());
    }
}
