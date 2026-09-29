package io.forgeloop.control.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Validated opt-in limits copied from a repository policy into each submitted run. */
@Embeddable
public class AgentLoopBudget {
    @Column(name = "agent_loop_max_tool_calls") private Integer maxToolCalls;
    @Column(name = "agent_loop_max_tokens") private Integer maxTokens;
    @Column(name = "agent_loop_max_wall_seconds") private Integer maxWallSeconds;
    @Column(name = "agent_loop_max_conversation_bytes") private Integer maxConversationBytes;

    protected AgentLoopBudget() { }

    public AgentLoopBudget(int maxToolCalls, int maxTokens, int maxWallSeconds, int maxConversationBytes) {
        this.maxToolCalls = maxToolCalls;
        this.maxTokens = maxTokens;
        this.maxWallSeconds = maxWallSeconds;
        this.maxConversationBytes = maxConversationBytes;
        validate();
    }

    /** Allows a nullable embedded value to represent "disabled" while rejecting partial database state. */
    public void validate() {
        if (maxToolCalls == null && maxTokens == null && maxWallSeconds == null && maxConversationBytes == null) return;
        if (maxToolCalls == null || maxTokens == null || maxWallSeconds == null || maxConversationBytes == null
                || maxToolCalls < 1 || maxToolCalls > 1_000
                || maxTokens < 10_000 || maxTokens > 100_000_000
                || maxWallSeconds < 60 || maxWallSeconds > 14_400
                || maxConversationBytes < 65_536 || maxConversationBytes > 4_194_304)
            throw new IllegalArgumentException("Agent loop budget is outside policy bounds");
    }

    public AgentLoopBudget copy() {
        validate();
        if (maxToolCalls == null) return null;
        return new AgentLoopBudget(maxToolCalls, maxTokens, maxWallSeconds, maxConversationBytes);
    }

    public Integer getMaxToolCalls() { return maxToolCalls; }
    public Integer getMaxTokens() { return maxTokens; }
    public Integer getMaxWallSeconds() { return maxWallSeconds; }
    public Integer getMaxConversationBytes() { return maxConversationBytes; }
}
