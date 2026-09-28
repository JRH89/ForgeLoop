package io.forgeloop.control.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueConversationMessageRepository extends JpaRepository<IssueConversationMessage, Long> {
    List<IssueConversationMessage> findByConversation_IdOrderByCreatedAtAscIdAsc(String conversationId);
}
