package io.forgeloop.control.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** A bounded user or assistant message. Provider credentials and provider prompts are never included here. */
@Entity
@Table(name = "issue_conversation_message")
public class IssueConversationMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "conversation_id", nullable = false)
    private IssueConversation conversation;
    @Column(nullable = false, length = 16) private String role;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Column(nullable = false) private Instant createdAt;

    protected IssueConversationMessage() { }

    public IssueConversationMessage(IssueConversation conversation, String role, String content) {
        if (!"USER".equals(role) && !"ASSISTANT".equals(role)) throw new IllegalArgumentException("Chat message role is invalid");
        if (content == null || content.isBlank() || content.length() > 4000) throw new IllegalArgumentException("Chat message is invalid");
        this.conversation = conversation;
        this.role = role;
        this.content = content.trim();
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public String getCreatedAt() { return createdAt.toString(); }
}
