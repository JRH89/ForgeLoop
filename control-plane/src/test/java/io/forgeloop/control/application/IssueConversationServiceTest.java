package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.forgeloop.control.domain.IssueConversation;
import io.forgeloop.control.domain.IssueConversationMessage;
import io.forgeloop.control.domain.IssueConversationMessageRepository;
import io.forgeloop.control.domain.IssueConversationRepository;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.Runner;
import io.forgeloop.control.integrations.github.GithubApi;
import io.forgeloop.control.integrations.github.GithubIssueReceipt;
import io.forgeloop.control.security.OperatorContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class IssueConversationServiceTest {
    private final IssueConversationRepository conversations = mock(IssueConversationRepository.class);
    private final IssueConversationMessageRepository messages = mock(IssueConversationMessageRepository.class);
    private final RepositoryConnectionService connections = mock(RepositoryConnectionService.class);
    private final OperatorContext operators = mock(OperatorContext.class);
    private final GithubApi github = mock(GithubApi.class);
    private final AuditLedgerService audit = mock(AuditLedgerService.class);
    private final ProviderActivityService activities = mock(ProviderActivityService.class);
    private final IssueConversationService service = new IssueConversationService(conversations, messages, connections,
            operators, github, audit, activities);

    @Test void startsAQueuedConversationWithUserTextOnly() {
        RepositoryConnection connection = mock(RepositoryConnection.class);
        when(connection.getOrganizationId()).thenReturn("org-1");
        when(connections.requireEnabled("acme/app")).thenReturn(connection);
        when(operators.subject()).thenReturn("operator-1");
        when(operators.organizationId()).thenReturn("org-1");
        when(conversations.save(any(IssueConversation.class))).thenAnswer(call -> call.getArgument(0));
        when(messages.save(any(IssueConversationMessage.class))).thenAnswer(call -> call.getArgument(0));
        when(messages.findByConversation_IdOrderByCreatedAtAscIdAsc(any())).thenReturn(List.of());

        IssueConversationView view = service.start("acme/app", "Add a timeout to exports.");

        assertEquals("PENDING", view.status());
        verify(messages).save(any(IssueConversationMessage.class));
        verify(audit).record(eq("ISSUE_CHAT_STARTED"), eq("ISSUE_CONVERSATION"), any(), eq("acme/app"));
        verify(github, never()).createIssue(anyLong(), any(), any(), any());
    }

    @Test void recordsChatUsageAndKeepsDraftingOnTheRunner() {
        IssueConversation conversation = runningConversation();
        when(operators.organizationId()).thenReturn("org-1");
        when(conversations.lockByIdAndOrganizationId("chat-1", "org-1")).thenReturn(Optional.of(conversation));
        when(messages.save(any(IssueConversationMessage.class))).thenAnswer(call -> call.getArgument(0));
        when(messages.findByConversation_IdOrderByCreatedAtAscIdAsc("chat-1")).thenReturn(List.of());

        IssueConversationView view = service.complete("chat-1", runner("runner-1", "org-1"),
                new IssueChatTurnResultInput(true, "I drafted the issue.", "Bound export duration", "Add a timeout.",
                        List.of("Timed-out jobs show a status."), "openai", "gpt-test", 20, 10, 500, true));

        assertEquals("READY", view.status());
        assertEquals("Bound export duration", view.draftTitle());
        verify(activities).record(eq("org-1"), eq("AI_CHAT"), eq("acme/app"), any(), eq("openai"), eq("gpt-test"),
                eq(20L), eq(10L), eq(500L), eq(true));
        verify(github, never()).createIssue(anyLong(), any(), any(), any());
    }

    @Test void requiresAdministratorReviewAndLeavesTheIssueUnassignedAndUnlabeled() {
        IssueConversation conversation = runningConversation();
        conversation.complete("runner-1", "Draft ready.", "Bound export duration", "Exports can hang.",
                List.of("Timeouts produce a clear status."), "openai", "gpt-test", 20, 10, 500, true);
        when(operators.organizationId()).thenReturn("org-1");
        when(conversations.lockByIdAndOrganizationId("chat-1", "org-1")).thenReturn(Optional.of(conversation));
        RepositoryConnection connection = mock(RepositoryConnection.class);
        when(connection.getInstallationId()).thenReturn(42L);
        when(connections.requireEnabled("acme/app")).thenReturn(connection);
        when(github.createIssue(42L, "acme/app", "Human-edited title", "Human-edited body\n\n### Acceptance criteria\n- [ ] Reviewed criterion\n\n---\nCreated from an administrator-reviewed ForgeLoop issue chat. The issue is not labeled or assigned automatically; repository intake policy controls when work starts."))
                .thenReturn(new GithubIssueReceipt(17, "https://github.com/acme/app/issues/17"));
        when(messages.findByConversation_IdOrderByCreatedAtAscIdAsc("chat-1")).thenReturn(List.of());

        IssueConversationView created = service.createIssue("chat-1", "Human-edited title", "Human-edited body", List.of("Reviewed criterion"));

        assertEquals("ISSUE_CREATED", created.status());
        assertEquals(17, created.issueNumber());
        verify(github).createIssue(eq(42L), eq("acme/app"), eq("Human-edited title"), any());
    }

    @Test void rejectsCrossOrganizationConversationLookup() {
        when(operators.organizationId()).thenReturn("org-2");
        when(conversations.lockByIdAndOrganizationId("chat-1", "org-2")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.get("chat-1"));
        verify(github, never()).createIssue(anyLong(), any(), any(), any());
    }

    @Test void requiresAdministratorBeforePublishingAnIssue() {
        doThrow(new AccessDeniedException("Organization administrator role is required"))
                .when(operators).requireAdministrator();
        assertThrows(AccessDeniedException.class,
                () -> service.createIssue("chat-1", "Reviewed title", "Reviewed body", List.of("Reviewed criterion")));
        verify(github, never()).createIssue(anyLong(), any(), any(), any());
    }

    private IssueConversation runningConversation() {
        IssueConversation conversation = new IssueConversation("org-1", "acme/app", "operator-1");
        try {
            var field = IssueConversation.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(conversation, "chat-1");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        conversation.claim("runner-1");
        return conversation;
    }

    private static Runner runner(String id, String organizationId) {
        Runner runner = mock(Runner.class);
        when(runner.getId()).thenReturn(id);
        when(runner.getOrganizationId()).thenReturn(organizationId);
        return runner;
    }
}
