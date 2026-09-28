package io.forgeloop.control.api;

import io.forgeloop.control.application.IssueConversationService;
import io.forgeloop.control.application.IssueConversationView;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/** Authenticated browser API for runner-assisted issue conversations. */
@Controller
public class IssueConversationController {
    private final IssueConversationService conversations;

    public IssueConversationController(IssueConversationService conversations) { this.conversations = conversations; }

    @QueryMapping public List<IssueConversationView> issueConversations(@Argument String repository) {
        return conversations.list(repository);
    }

    @QueryMapping public IssueConversationView issueConversation(@Argument String id) { return conversations.get(id); }

    @MutationMapping public IssueConversationView startIssueConversation(@Argument String repository, @Argument String message) {
        return conversations.start(repository, message);
    }

    @MutationMapping public IssueConversationView sendIssueConversationMessage(@Argument String conversationId, @Argument String message) {
        return conversations.send(conversationId, message);
    }

    @MutationMapping public IssueConversationView createIssueFromConversation(@Argument String conversationId,
            @Argument String title, @Argument String body, @Argument List<String> acceptanceCriteria) {
        return conversations.createIssue(conversationId, title, body, acceptanceCriteria);
    }
}
