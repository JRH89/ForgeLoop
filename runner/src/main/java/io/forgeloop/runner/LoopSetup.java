package io.forgeloop.runner;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** All task authority and mutable-run dependencies supplied by the later dispatch adapter. */
public record LoopSetup(String taskId, String repository, String leaseId, String role, String title, String specification,
                        List<String> ownedPrefixes, List<String> changedFiles, Path worktree, String baseSha,
                        ProviderExecutionPolicy providerPolicy, ConversationClient conversationClient,
                        LoopBudget budget, List<LoopGate> gates, ToolGateway toolGateway, ToolRegistry toolRegistry,
                        LoopJournal journal, Clock clock, LoopReporter reporter, AtomicBoolean leaseLost,
                        long budgetMicros, long spentCostMicros, EnforcementDescriptor enforcementDescriptor) {
    public LoopSetup {
        if (taskId == null || taskId.isBlank() || repository == null || repository.isBlank() || repository.length() > 200
                || leaseId == null || leaseId.isBlank() || role == null || title == null || title.isBlank()
                || specification == null || specification.isBlank() || ownedPrefixes == null || changedFiles == null || worktree == null
                || baseSha == null || !baseSha.matches("[0-9a-f]{40,64}") || providerPolicy == null || conversationClient == null || budget == null
                || gates == null || toolGateway == null || toolRegistry == null || journal == null || clock == null || reporter == null
                || leaseLost == null || budgetMicros < 0 || spentCostMicros < 0 || enforcementDescriptor == null)
            throw new IllegalArgumentException("Agent loop setup is incomplete");
        WorkerRolePolicy.require(role);
        ownedPrefixes = List.copyOf(ownedPrefixes);
        changedFiles = List.copyOf(changedFiles);
        worktree = worktree.toAbsolutePath().normalize();
        gates = List.copyOf(gates);
    }

    /** Keeps callers from the dormant core compatible while defaulting to the built-in enforcement policy. */
    public LoopSetup(String taskId, String repository, String leaseId, String role, String title, String specification,
                     List<String> ownedPrefixes, List<String> changedFiles, Path worktree, String baseSha,
                     ProviderExecutionPolicy providerPolicy, ConversationClient conversationClient,
                     LoopBudget budget, List<LoopGate> gates, ToolGateway toolGateway, ToolRegistry toolRegistry,
                     LoopJournal journal, Clock clock, LoopReporter reporter, AtomicBoolean leaseLost,
                     long budgetMicros, long spentCostMicros) {
        this(taskId, repository, leaseId, role, title, specification, ownedPrefixes, changedFiles, worktree, baseSha,
                providerPolicy, conversationClient, budget, gates, toolGateway, toolRegistry, journal, clock, reporter,
                leaseLost, budgetMicros, spentCostMicros, EnforcementDescriptor.defaults(gates));
    }
}
