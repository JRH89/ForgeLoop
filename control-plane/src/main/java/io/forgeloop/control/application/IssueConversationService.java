package io.forgeloop.control.application;

import io.forgeloop.control.domain.IssueConversation;
import io.forgeloop.control.domain.IssueConversationMessage;
import io.forgeloop.control.domain.IssueConversationMessageRepository;
import io.forgeloop.control.domain.IssueConversationRepository;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.Runner;
import io.forgeloop.control.integrations.github.GithubApi;
import io.forgeloop.control.security.OperatorContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates chat turns on customer runners and requires a separate human action to publish an issue. */
@Service
public class IssueConversationService {
    private static final int MAX_MESSAGES = 40;
    private static final List<String> PROVIDERS = List.of("anthropic", "openai", "gemini", "local");
    private final IssueConversationRepository conversations;
    private final IssueConversationMessageRepository messages;
    private final RepositoryConnectionService connections;
    private final OperatorContext operators;
    private final GithubApi github;
    private final AuditLedgerService audit;
    private final ProviderActivityService activities;

    public IssueConversationService(IssueConversationRepository conversations, IssueConversationMessageRepository messages,
                                   RepositoryConnectionService connections, OperatorContext operators, GithubApi github,
                                   AuditLedgerService audit, ProviderActivityService activities) {
        this.conversations = conversations;
        this.messages = messages;
        this.connections = connections;
        this.operators = operators;
        this.github = github;
        this.audit = audit;
        this.activities = activities;
    }

    @Transactional
    public IssueConversationView start(String repository, String message) {
        operators.requireOperator();
        RepositoryConnection connection = connections.requireEnabled(repository);
        String content = bounded(message, 4000, "message");
        IssueConversation conversation = conversations.save(new IssueConversation(connection.getOrganizationId(), repository, operators.subject()));
        messages.save(new IssueConversationMessage(conversation, "USER", content));
        audit.record("ISSUE_CHAT_STARTED", "ISSUE_CONVERSATION", conversation.getId(), repository);
        return view(conversation);
    }

    @Transactional
    public IssueConversationView send(String conversationId, String message) {
        operators.requireOperator();
        IssueConversation conversation = requireConversation(conversationId);
        String content = bounded(message, 4000, "message");
        List<IssueConversationMessage> existing = messages.findByConversation_IdOrderByCreatedAtAscIdAsc(conversationId);
        if (existing.size() >= MAX_MESSAGES) throw new IllegalStateException("This chat reached its message limit. Start another issue chat to continue.");
        conversation.queueNextTurn();
        messages.save(new IssueConversationMessage(conversation, "USER", content));
        audit.record("ISSUE_CHAT_TURN_QUEUED", "ISSUE_CONVERSATION", conversationId, "message-count=" + (existing.size() + 1));
        return view(conversation);
    }

    @Transactional(readOnly = true)
    public IssueConversationView get(String conversationId) {
        IssueConversation conversation = conversations.findByIdAndOrganizationId(conversationId, operators.organizationId())
                .orElseThrow(() -> new IllegalArgumentException("Issue conversation not found"));
        return view(conversation);
    }

    @Transactional(readOnly = true)
    public List<IssueConversationView> list(String repository) {
        connections.requireEnabled(repository);
        return conversations.findTop50ByOrganizationIdAndRepositoryOrderByUpdatedAtDesc(operators.organizationId(), repository)
                .stream().map(this::view).toList();
    }

    @Transactional
    public IssueChatTurnGrant claim(Runner runner) {
        if (!runner.isEnabled()) throw new IllegalStateException("Runner is disabled");
        Instant staleBefore = Instant.now().minus(Duration.ofMinutes(30));
        List<IssueConversation> claimable = conversations.lockClaimableForOrganization(runner.getOrganizationId(), staleBefore,
                PageRequest.of(0, 1));
        if (claimable.isEmpty()) return null;
        IssueConversation conversation = claimable.getFirst();
        if (conversation.requeueExpiredClaim(staleBefore))
            audit.record("ISSUE_CHAT_REQUEUED", "ISSUE_CONVERSATION", conversation.getId(), "expired-runner-claim");
        conversation.claim(runner.getId());
        audit.record("ISSUE_CHAT_CLAIMED", "ISSUE_CONVERSATION", conversation.getId(), runner.getId());
        return IssueChatTurnGrant.from(conversation, messages.findByConversation_IdOrderByCreatedAtAscIdAsc(conversation.getId()));
    }

    @Transactional
    public IssueConversationView complete(String conversationId, Runner runner, IssueChatTurnResultInput input) {
        IssueConversation conversation = conversations.lockByIdAndOrganizationId(conversationId, runner.getOrganizationId())
                .orElseThrow(() -> new IllegalArgumentException("Issue conversation not found"));
        if (input == null) throw new IllegalArgumentException("Issue chat result is required");
        if (!input.passed()) {
            validateOptionalUsage(input);
            conversation.fail(runner.getId(), "The runner could not draft this issue.", input.provider(), input.model(),
                    input.inputTokens(), input.outputTokens(), input.estimatedCostMicros(), input.costKnown());
            recordUsage(conversation, input, null);
            audit.record("ISSUE_CHAT_FAILED", "ISSUE_CONVERSATION", conversationId, input.provider() == null ? "provider-no-result" : input.model());
            return view(conversation);
        }
        conversation.complete(runner.getId(), bounded(input.assistantMessage(), 2000, "assistant message"),
                bounded(input.title(), 200, "title"), bounded(input.body(), 12000, "body"), validateCriteria(input.acceptanceCriteria()),
                input.provider(), input.model(), input.inputTokens(), input.outputTokens(), input.estimatedCostMicros(), input.costKnown());
        IssueConversationMessage assistant = messages.save(new IssueConversationMessage(conversation, "ASSISTANT", input.assistantMessage().trim()));
        recordUsage(conversation, input, assistant.getId());
        audit.record("ISSUE_CHAT_DRAFT_READY", "ISSUE_CONVERSATION", conversationId, input.model());
        return view(conversation);
    }

    @Transactional
    public IssueConversationView createIssue(String conversationId, String title, String body, List<String> criteria) {
        operators.requireAdministrator();
        IssueConversation conversation = requireConversation(conversationId);
        if (!"READY".equals(conversation.getStatus())) throw new IllegalStateException("Review a completed issue draft before creating the GitHub issue");
        String approvedTitle = bounded(title, 200, "title");
        String approvedBody = bounded(body, 12000, "body");
        List<String> acceptedCriteria = validateCriteria(criteria);
        RepositoryConnection connection = connections.requireEnabled(conversation.getRepository());
        String issueBody = approvedBody + "\n\n### Acceptance criteria\n"
                + acceptedCriteria.stream().map(item -> "- [ ] " + item).reduce((left, right) -> left + "\n" + right).orElse("")
                + "\n\n---\nCreated from an administrator-reviewed ForgeLoop issue chat. The issue is not labeled or assigned automatically; repository intake policy controls when work starts.";
        var receipt = github.createIssue(connection.getInstallationId(), conversation.getRepository(), approvedTitle, issueBody);
        conversation.createIssue(receipt.number(), receipt.url(), approvedTitle, approvedBody, acceptedCriteria);
        audit.record("ISSUE_CHAT_ISSUE_CREATED", "ISSUE_CONVERSATION", conversationId, receipt.number() + "|" + receipt.url());
        return view(conversation);
    }

    private IssueConversation requireConversation(String id) {
        return conversations.lockByIdAndOrganizationId(id, operators.organizationId())
                .orElseThrow(() -> new IllegalArgumentException("Issue conversation not found"));
    }

    private IssueConversationView view(IssueConversation conversation) {
        List<IssueChatMessageView> history = messages.findByConversation_IdOrderByCreatedAtAscIdAsc(conversation.getId()).stream()
                .map(message -> new IssueChatMessageView(message.getRole(), message.getContent(), message.getCreatedAt())).toList();
        return IssueConversationView.from(conversation, history);
    }

    private void recordUsage(IssueConversation conversation, IssueChatTurnResultInput input, Long assistantMessageId) {
        if (input.provider() == null) return;
        String sourceId = conversation.getId() + ":" + (assistantMessageId == null ? java.util.UUID.randomUUID() : assistantMessageId);
        activities.record(conversation.getOrganizationId(), "AI_CHAT", conversation.getRepository(), sourceId,
                input.provider(), input.model(), input.inputTokens(), input.outputTokens(), input.estimatedCostMicros(), input.costKnown());
    }

    private static void validateOptionalUsage(IssueChatTurnResultInput input) {
        if (input.provider() == null) {
            if (input.model() != null || input.inputTokens() != 0 || input.outputTokens() != 0
                    || input.estimatedCostMicros() != 0 || input.costKnown())
                throw new IllegalArgumentException("Issue chat usage metadata is incomplete");
            return;
        }
        validateUsage(input.provider(), input.model(), input.inputTokens(), input.outputTokens(), input.estimatedCostMicros(), input.costKnown());
    }

    private static void validateUsage(String provider, String model, long inputTokens, long outputTokens,
                                      long estimatedCostMicros, boolean costKnown) {
        if (!PROVIDERS.contains(provider) || model == null || model.isBlank() || model.length() > 200
                || inputTokens < 0 || outputTokens < 0 || estimatedCostMicros < 0 || (!costKnown && estimatedCostMicros != 0))
            throw new IllegalArgumentException("Issue chat usage metadata is invalid");
    }

    private static List<String> validateCriteria(List<String> criteria) {
        if (criteria == null || criteria.isEmpty() || criteria.size() > 10)
            throw new IllegalArgumentException("Issue draft must include 1 to 10 acceptance criteria");
        return criteria.stream().map(item -> bounded(item, 400, "acceptance criterion")).toList();
    }

    private static String bounded(String value, int maxLength, String label) {
        if (value == null || value.isBlank() || value.length() > maxLength)
            throw new IllegalArgumentException("Issue chat " + label + " is invalid");
        return value.trim();
    }
}
